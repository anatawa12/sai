package com.anatawa12.sai.stia.ir

import com.anatawa12.sai.*
import com.anatawa12.sai.ast.*
import com.anatawa12.sai.stia.*
import com.anatawa12.sai.stia.ir.IrBinaryOperatorType.*
import com.anatawa12.sai.stia.ir.IrUnaryOperatorType.*

//*
class IrNodeGenerator {
    private val setThisFn = -1

    fun transform(node: ScriptNode): IrScope {
        replaceThisFunAssign(node)
        return processScope(node)
    }

    private fun replaceThisFunAssign(node: ScriptNode) {
        val block = node.firstOrNull() ?: return
        if (block.type != Token.BLOCK) return
        val expr = block.firstOrNull() ?: return
        if (expr.type != Token.EXPR_VOID) return
        val setName = expr.singleOrNull() ?: return
        if (setName.type != Token.SETNAME) return
        val (name, thisFn) = setName.asPairOrNull() ?: return
        if (name.type != Token.BINDNAME) return
        if (thisFn.type != Token.THISFN) return
        block.replaceChild(expr, Node(setThisFn, name))
    }

    // region expression accepter
    private fun visitBinary(node: Node, type: IrBinaryOperatorType): IrExpression {
        val (left, right) = node.asPair()
        return IrBinaryOperator(type, visitExpr(left), visitExpr(right))
    }

    private fun visitUnary(node: Node, type: IrUnaryOperatorType): IrExpression {
        val operand = node.single()
        return IrUnaryOperator(type, visitExpr(operand))
    }

    protected fun visitExpr(node: Node): IrExpression = when (node.type) {
        Token.BITOR -> visitBinary(node, BITOR)
        Token.BITXOR -> visitBinary(node, BITXOR)
        Token.BITAND -> visitBinary(node, BITAND)
        Token.LSH -> visitBinary(node, LSH)
        Token.RSH -> visitBinary(node, RSH)
        Token.URSH -> visitBinary(node, URSH)

        Token.EQ -> visitBinary(node, EQ)
        Token.NE -> IrUnaryOperator(NOT, visitBinary(node, EQ))
        Token.LT -> visitBinary(node, LT)
        Token.LE -> visitBinary(node, LE)
        Token.GT -> visitBinary(node, GT)
        Token.GE -> visitBinary(node, GE)
        Token.IN -> visitBinary(node, IN)
        Token.INSTANCEOF -> visitBinary(node, INSTANCEOF)
        Token.SHEQ -> visitBinary(node, SHEQ)
        Token.SHNE -> visitBinary(node, SHNE)

        Token.SUB -> visitBinary(node, SUB)
        Token.MUL -> visitBinary(node, MUL)
        Token.DIV -> visitBinary(node, DIV)
        Token.MOD -> visitBinary(node, MOD)

        Token.ADD -> visitBinary(node, ADD)

        Token.NOT -> visitUnary(node, NOT)
        Token.BITNOT -> visitUnary(node, BITNOT)
        Token.POS -> visitUnary(node, POSITIVE)
        Token.NEG -> visitUnary(node, NEGATIVE)
        Token.DELPROP, // delete expr
        -> {
            unsupported("delete expr")
            /*
            val operand = node.single()
            processExprs(operand)
            node.valueInfo.currently(BooleanType)
            */
        }
        // typeof expr
        Token.TYPEOF -> visitUnary(node, TYPEOF)
        Token.GETPROP,
        Token.GETELEM,
        -> {
            val (owner, name) = node.asPair()
            IrGetProperty(visitExpr(owner), visitExpr(name), node.type == Token.GETPROP)
        }
        Token.SETPROP,
        Token.SETELEM,
        -> {
            val (owner, name, value) = node.asTriple()
            IrSetProperty(visitExpr(owner), visitExpr(name), visitExpr(value), node.type == Token.SETPROP)
        }
        Token.NEW,
        Token.CALL,
        -> {
            val isNewInstance = node.type == Token.NEW
            val function = node.first()
            val args = node.drop(1)
            IrNewOrCall(visitExpr(function), args.map(::visitExpr), isNewInstance)
        }
        Token.THIS,
        -> unsupported("THIS")
        // literal
        Token.NUMBER -> IrNumberLiteral(node.double)
        Token.STRING -> IrStringLiteral(node.string)
        Token.NULL -> IrNullLiteral()
        Token.FALSE -> IrBooleanLiteral(false)
        Token.TRUE -> IrBooleanLiteral(true)
        // regexp literal
        Token.REGEXP -> IrRegexpLiteral(node as RegExpLiteral)
        Token.LOCAL_LOAD -> unsupported("internal variable")
        // array literal
        Token.ARRAYLIT -> unsupported("array literal")
        // object literal
        Token.OBJECTLIT -> unsupported("object literal")
        // void expr;
        Token.VOID -> visitUnary(node, VOID)
        // typeof name: this is Name
        Token.TYPEOFNAME,
        -> unsupported("TYPEOFNAME")
        Token.USE_STACK, // use the value on the stack,
        -> unsupported("USE_STACK")
        Token.SETPROP_OP, // a.b op= c
        Token.SETELEM_OP, // a.[b] op= c
        -> unsupported("OPERATOR_ASSIGN")
        // expr, expr, expr....
        Token.COMMA -> IrCommaExpr(node.map(::visitExpr))
        Token.OR -> visitBinary(node, OR)
        Token.AND -> visitBinary(node, AND)
        Token.INC, // ++ expr or expr ++
        Token.DEC, // -- expr or expr --
        -> {
            val prop = node.getExistingIntProp(Node.INCRDECR_PROP)
            val isDec = prop and Node.DECR_FLAG != 0
            val isSuf = prop and Node.POST_FLAG != 0

            val child = node.single()
            when (child.type) {
                Token.NAME -> IrNameIncDec(child.string, isDec, isSuf)
                Token.GETELEM,
                Token.GETPROP,
                -> {
                    val (owner, name) = node.asPair()
                    IrPropertyIncDec(visitExpr(owner), visitExpr(name),
                        child.type == Token.GETPROP, isDec, isSuf)
                }
                else -> unsupported("INC/DEC with $child.type")
            }
        }

        Token.HOOK, // condition ? then : else
        -> {
            val (condition, ifTrue, ifFalse) = node.asTriple()
            IrConditional(visitExpr(condition), visitExpr(ifTrue), visitExpr(ifFalse))
        }

        Token.SETNAME -> {
            val (name, variable) = node.asPair()
            IrSetName(name.string, visitExpr(variable))
        }
        Token.NAME -> IrGetName(node.string)
        Token.BINDNAME -> unsupported("BINDNAME")

        // reference to this function
        Token.THISFN -> unsupported("THISFN")

        // literal or root definition
        Token.FUNCTION -> unsupported("FUNCTION")

        else -> unsupported("unsupported token for expression: ${node.type}")
    }
    // endregion expression accepter

    private val targetMapping = mutableMapOf<Node, IrJumpTarget>()
    private fun getTargetOf(target: Node): IrJumpTarget {
        return targetMapping.getOrPut(target) { IrJumpTarget() }
    }

    private fun Node.asJump() = this as Jump

    private fun visitStatement(node: Node, parent: Node): IrStatement = when (node.type) {
        Token.RETURN -> IrReturn(node.firstChild.nullable()?.let(::visitExpr))

        // jumping instructions
        Token.GOTO -> IrGoto(getTargetOf(node.asJump().target))
        Token.JSR -> IrJsr(getTargetOf(node.asJump().target))
        // jump if true
        Token.IFEQ -> IrIfFalse(visitExpr(node.single()), getTargetOf(node.asJump().target))
        // jump if false
        Token.IFNE -> IrIfTrue(visitExpr(node.single()), getTargetOf(node.asJump().target))
        Token.BREAK -> IrGoto(getTargetOf(node.asJump().jumpStatement.target))
        Token.CONTINUE -> IrGoto(getTargetOf(node.asJump().jumpStatement.`continue`))

        // switch statement
        Token.SWITCH -> IrSwitch(visitExpr(node.first()),
            node.drop(1).map { visitExpr(it.single()) to getTargetOf(it.asJump().target) })

        Token.TARGET -> getTargetOf(node)

        // statements or expressions block
        // labeled block
        Token.LABEL -> processBlock(node)
        //Token.WITH -> visitWith(node)

        // scope block
        Token.BLOCK -> processScope(node)
        Token.LOOP -> processScope(node)

        // contextual local variable (hidden)
        Token.LOCAL_BLOCK -> IrInternalScope(node.map { visitStatement(it, node) }, node.toId())

        Token.TRY -> transformTry(node as Jump, parent)
        Token.FINALLY -> processBlock(node)

        Token.VAR,
        Token.LET,
        Token.CONST,
        -> IrVariableDecl(
            node.map { it.string to it.singleOrNull()?.let(::visitExpr) },
            VariableKind.getByNodeType(node.type),
        )

        Token.THROW -> IrThrow(visitExpr(node.single()))
        Token.RETHROW -> IrRethrow(node.getIdByLocalBlockProp())
        // ; // empty statement
        Token.EMPTY -> IrEmptyStatement()

        // when became needed, add flag for result
        Token.EXPR_VOID, Token.EXPR_RESULT -> IrExpressionStatement(visitExpr(node.single()))

        // literal or root definition
        Token.FUNCTION -> IrFunctionStatement(/*TODO*/)

        setThisFn -> IrSetThisFn(node.single().string)
        else -> unsupported("unsupported token for statement: ${node.type}")
    }

    // only for try but no inline fun was supported for local functions
    private fun Iterator<Node>.getNext() = if (hasNext()) next() else unsupported("TRY format")
    private fun Iterator<Node>.getNextOrNull() = if (hasNext()) next() else null

    @JvmName("getNextInline")
    private inline fun <reified T : Node> Iterator<Node>.getNext(): T {
        return getNext() as? T ?: unsupported("TRY format")
    }

    @JvmName("getNextInline1")
    private fun Iterator<Node>.getNext(type: Int): Node =
        getNext().takeIf { it.type == type } ?: unsupported("TRY format")

    @JvmName("getNextInline")
    private inline fun <reified T : Node> Iterator<Node>.getNext(type: Int): T =
        getNext<T>().takeIf { it.type == type } ?: unsupported("TRY format")

    @JvmName("getNextInline1")
    private fun Iterator<Node>.getNext(expect: Node): Node =
        getNext().takeIf { it == expect } ?: unsupported("TRY format")

    @JvmName("getNextInline")
    private inline fun <reified T : Node> Iterator<Node>.getNext(expect: Node): T =
        getNext<T>().takeIf { it == expect } ?: unsupported("TRY format")

    /**
     * transform 'try' statement to be without 'with' statement.
     * the IRFactory uses 'with' for catch-cause-scope but Stia will not support 'with' so convert to 'let' declaration.
     */
    private fun transformTry(tryStat: Jump, localBlock: Node) : IrStatement {
        if (tryStat.type != Token.TRY) Kit.codeBug()
        if (localBlock.type != Token.LOCAL_BLOCK)
            unsupported("parent of TRY is not LOCAL_BLOCK")
        if (localBlock.singleOrNull() != tryStat)
            unsupported("child of LOCAL_BLOCK must be single TRY block")

        val statements = mutableListOf<IrStatement>()
        fun <T : Node> T.add(): T = apply { statements.add(visitStatement(this, tryStat)) }

        val catchTarget = tryStat.target.nullable()
            ?: return processBlock(tryStat)
        // if there's no catch, no transform should be required

        val iter = tryStat.iterator()
        iter.getNext(Token.BLOCK).add()
        val gotoEndCatch = iter.getNext<Jump>(Token.GOTO).add()
        iter.getNext(catchTarget).add()
        val endCatch = gotoEndCatch.target
        var cur: Node?
        while (true) {
            cur = iter.getNextOrNull()
            if (cur?.type != Token.LOCAL_BLOCK) break
            val catchLocalBlock: Node = cur

            // verification.
            /*
            catchLocalBlock = LOCAL_BLOCK
              CATCH_SCOPE (LOCAL_BLOCK_PROP = catchLocalBlock)
                NAME
                LOCAL_LOAD (LOCAL_BLOCK_PROP = localBlock)
              BLOCK
                ENTER_WITH
                  LOCAL_LOAD (LOCAL_BLOCK_PROP = catchLocalBlock)
                WITH
                  BLOCK
                    // if "catch (e if condition)"
                    IFNE target
                      ANY
                    catchBlock = BLOCK
                    target = TARGET
                    // else
                    catchBlock = this
                    // endif
                LEAVE_WITH
            */
            /*
            catchBlock = BLOCK
              ANY
              LEAVEWITH
              GOTO endCatch
            */
            val (catchScope, withOuterBlock) = catchLocalBlock.asPairOrNull() ?: unsupported("TRY format")
            if (catchScope.type != Token.CATCH_SCOPE) unsupported("TRY format")
            if (catchScope.getProp(Node.LOCAL_BLOCK_PROP) != catchLocalBlock) unsupported("TRY format")
            if (withOuterBlock.type != Token.BLOCK) unsupported("TRY format")
            val (name, catcScopeExpr) = catchScope.asPairOrNull() ?: unsupported("TRY format")
            if (catcScopeExpr.type != Token.LOCAL_LOAD) unsupported("TRY format")
            if (catcScopeExpr.getProp(Node.LOCAL_BLOCK_PROP) != localBlock) unsupported("TRY format")
            val (enterWith, with, leaveWith) = withOuterBlock.asTripleOrNull() ?: unsupported("TRY format")
            val localLoad1 = enterWith.singleOrNull() ?: unsupported("TRY format")
            if (localLoad1.type != Token.LOCAL_LOAD) unsupported("TRY format")
            if (localLoad1.getProp(Node.LOCAL_BLOCK_PROP) != catchLocalBlock) unsupported("TRY format")
            if (with.type != Token.WITH) unsupported("TRY format")
            val withBody = with.singleOrNull() ?: unsupported("TRY format")
            if (leaveWith.type != Token.LEAVEWITH) unsupported("TRY format")
            if (withBody.type != Token.BLOCK) unsupported("TRY format")
            val catchBlock: Node
            if (withBody.lastChild.type == Token.TARGET) {
                // catch (e if condition)
                val (gotoIf, catchBlock1, target) = withBody.asTripleOrNull() ?: unsupported("TRY format")
                if (gotoIf.type != Token.IFNE) unsupported("TRY format")
                if ((gotoIf as Jump).target != target) unsupported("TRY format")
                catchBlock = catchBlock1
            } else {
                // catch
                catchBlock = withBody
            }
            val (leaveWith1, gotoEndCatch1) = catchBlock.takeLastOrNull(2) ?:  unsupported("TRY format")
            if (leaveWith1.type != Token.LEAVEWITH) unsupported("TRY format")
            if (gotoEndCatch1.type != Token.GOTO) unsupported("TRY format")
            if ((gotoEndCatch1 as Jump).target != endCatch) unsupported("TRY format")
            catchBlock.removeChild(leaveWith1)
            catchBlock.removeChild(gotoEndCatch1)

            val scope = IrScope(
                listOf(
                    IrVariableDecl(listOf(name.string to IrConvertException(localBlock.toId())),
                        VariableKind.LET),
                    visitStatement(withBody, with),
                ),
                mapOf(name.string to IrSymbol(name.string, VariableKind.LET))
            )
            statements.add(scope)
        }
        cur?.add()
        for (node in iter) {
            node.add()
        }
        return IrBlock(statements)
    }

    private fun processScope(node: Node): IrScope {
        val statements = node.map { visitStatement(it, node) }
        return IrScope(statements, (node as? Scope)?.symbolTable
            ?.mapValues { IrSymbol(it.value.name, VariableKind.getByNodeType(it.value.declType)) }.orEmpty())
    }

    private fun processBlock(node: Node): IrBlock {
        val statements = node.map { visitStatement(it, node) }
        return IrBlock(statements)
    }

    private fun unsupported(msg: Any): Nothing {
        error(msg)
    }

    private val idMap = mutableMapOf<Any, IrInternalVariableId>()

    private fun Node.getIdByLocalBlockProp(): IrInternalVariableId =
        idMap.getOrPut(getProp(Node.LOCAL_BLOCK_PROP), ::IrInternalVariableId)
    private fun Any.toId(): IrInternalVariableId =
        idMap.getOrPut(this, ::IrInternalVariableId)
}
// */
