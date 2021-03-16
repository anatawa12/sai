package com.anatawa12.sai.stia.ir

import com.anatawa12.sai.*
import com.anatawa12.sai.ast.*
import com.anatawa12.sai.stia.*
import com.anatawa12.sai.stia.ir.IrBinaryOperatorType.*
import com.anatawa12.sai.stia.ir.IrUnaryOperatorType.*

//*
class IrNodeGenerator {
    private val setThisFn = -1
    private var scriptNodeBacked: ScriptNode? = null
    private var scriptNode: ScriptNode
        get() = scriptNodeBacked ?: error("scriptNode not initialized")
        set(value) {
            scriptNodeBacked = value
        }

    fun transform(node: ScriptNode): IrFunctionInformation {
        val cache = scriptNodeBacked
        try {
            scriptNode = node
            return transformBacked(node)
        } finally {
            scriptNodeBacked = cache
        }
    }

    private fun transformBacked(node: ScriptNode): IrFunctionInformation {
        replaceThisFunAssign(node)
        val asFunction = node as? FunctionNode
        return IrFunctionInformation(
            kind = when (asFunction?.functionType) {
                null -> FunctionKind.TOP_LEVEL
                FunctionNode.FUNCTION_STATEMENT -> FunctionKind.FUNCTION
                FunctionNode.FUNCTION_EXPRESSION -> FunctionKind.FUNCTION
                FunctionNode.FUNCTION_EXPRESSION_STATEMENT -> FunctionKind.FUNCTION
                FunctionNode.ARROW_FUNCTION -> FunctionKind.LAMBDA
                else -> error("invalid functionType: ${asFunction.functionType}")
            },
            name = asFunction?.name ?: "<top-level>",
            body = processScope(node),
        )
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
        Token.SETPROP_OP, // a.b op= c
        Token.SETELEM_OP, // a.[b] op= c
        -> {
            val (owner, name, value) = node.asTriple()
            val (useStack, operand) = value.asPair()
            IrPropertyOperatorAssign(visitExpr(owner), visitExpr(name),
                node.type == Token.SETPROP_OP, operatorAssignBinaryOperator(value.type),
                visitExpr(operand))
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

    private fun operatorAssignBinaryOperator(token: Int): IrBinaryOperatorType = when (token) {
        Token.BITOR -> BITOR
        Token.BITXOR -> BITXOR
        Token.BITAND -> BITAND
        Token.LSH -> LSH
        Token.RSH -> RSH
        Token.URSH -> URSH

        Token.ADD -> ADD
        Token.SUB -> SUB
        Token.MUL -> MUL
        Token.DIV -> DIV
        Token.MOD -> MOD
        else -> unsupported("op= assign operator; $token")
    }

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
            node.map { IrDeclVariable(it.string, it.singleOrNull()?.let(::visitExpr)) },
            VariableKind.getByNodeType(node.type),
        )

        Token.THROW -> IrThrow(visitExpr(node.single()))
        Token.RETHROW -> IrRethrow(node.getIdByLocalBlockProp())
        // ; // empty statement
        Token.EMPTY -> IrEmptyStatement()

        // when became needed, add flag for result
        Token.EXPR_VOID, Token.EXPR_RESULT -> IrExpressionStatement(visitExpr(node.single()))

        // literal or root definition
        Token.FUNCTION -> {
            val funcIndex = node.getExistingIntProp(Node.FUNCTION_PROP)
            val functionNode = scriptNode.getFunctionNode(funcIndex)
            IrFunctionStatement(transform(functionNode))
        }

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
     * the IRFactory uses 'with' for catch-cause-scope but
     * Stia will not support 'with' block so convert to 'let' declaration.
     *
     * TRY [catch: #243] [finally: #297] 135 [local_block: #221]
     *     BLOCK 135
     *         TRY_BODY
     *   $if (contains catch) {
     *     GOTO [target: #294]
     *     TARGET #243
     *     LOCAL_BLOCK #244
     *       $(
     *         CATCH_SCOPE [catch_scope_prop: 0] [local_block: #244]
     *             NAME e
     *             LOCAL_LOAD [local_block: #221]
     *         BLOCK 139
     *             ENTERWITH
     *                 LOCAL_LOAD [local_block: #244]
     *             WITH 139
     *                 BLOCK 139
     *                     IFNE [target: #267]
     *                         CONDITION
     *                     BLOCK 139
     *                         CATCH_BODY
     *                         LEAVEWITH
     *                         GOTO [target: #294]
     *                     TARGET #267
     *             LEAVEWITH
     *       )*
     *       $(
     *         CATCH_SCOPE [catch_scope_prop: 0] [local_block: #244]
     *             NAME e
     *             LOCAL_LOAD [local_block: #221]
     *         BLOCK 139
     *             ENTERWITH
     *                 LOCAL_LOAD [local_block: #244]
     *             WITH 139
     *                 BLOCK 139
     *                     CATCH_BODY
     *                     LEAVEWITH
     *                     GOTO [target: #294]
     *             LEAVEWITH
     *       )?
     *   $if (all catches are condition throw) {
     *     RETHROW [local_block: #221]
     *   }
     *     TARGET #294
     *   }
     *   $if (contains finally) {
     *     JSR [target: #297]
     *     GOTO [target: #310]
     *     TARGET #297
     *     FINALLY [local_block: #221]
     *         BLOCK 143
     *             FINALY
     *     TARGET #310
     *   }
     */
    @Suppress("UNUSED_VARIABLE")
    private fun transformTry(tryStat: Jump, localBlock: Node) : IrStatement {
        if (tryStat.type != Token.TRY) Kit.codeBug()
        val iter = tryStat.iterator()
        val block = iter.next()
        var cur = iter.getNextOrNull()

        val tryBlock = processScope(block)
        var conditionalCatches: List<IrConditionalCatch> = emptyList()
        var simpleCatch: IrSimpleCatch? = null
        var finally: IrFinally? = null

        if (cur?.type == Token.GOTO) {
            // means has catch
            val gotoEndCatches = cur
            val catchTarget = iter.next()
            val catchesBlock = iter.next()
            cur = iter.next()
            val rethrow = cur.takeIf { it.type == Token.RETHROW }
            if (rethrow != null)
                cur = iter.next()
            val endCatches = cur
            cur = iter.getNextOrNull()

            val catchNodes = catchesBlock.chunked(2) { (catch, block) -> catch to block }
            conditionalCatches = if (rethrow != null) {
                processConditional(catchNodes)
            } else {
                processConditional(catchNodes.dropLast(1))
            }
            simpleCatch = if (rethrow != null) null else processSimpleCatch(catchNodes.last())
        }
        if (cur?.type == Token.JSR) {
            // means has finally
            val jsr = cur
            val gotoEnd = iter.next()
            val finallyTarget = iter.next()
            val finallyNode = iter.next()
            val endTarget = iter.next()
            val finallyBlock = finallyNode.single()

            finally = IrFinally(processScope(finallyBlock))
        }

        return IrTryCatch(
            tryBlock = tryBlock,
            conditionals = conditionalCatches,
            simple = simpleCatch,
            finally = finally,
        )
    }

    private fun processConditional(scopeBlockPairs: List<Pair<Node, Node>>): List<IrConditionalCatch> =
        scopeBlockPairs.map { (scope, withOuter) ->
            val (enter, with, leave) = withOuter.asTriple()
            val withInner = with.single()
            val (ifne, block, target) = withInner.asTriple()

            check(scope.type == Token.CATCH_SCOPE) { "invalid try" }
            check(withOuter.type == Token.BLOCK) { "invalid try" }
            check(enter.type == Token.ENTERWITH) { "invalid try" }
            check(with.type == Token.WITH) { "invalid try" }
            check(leave.type == Token.LEAVEWITH) { "invalid try" }
            check(withInner.type == Token.BLOCK) { "invalid try" }
            check(ifne.type == Token.IFNE) { "invalid try" }
            check(block.type == Token.BLOCK) { "invalid try" }
            check(target.type == Token.TARGET) { "invalid try" }

            IrConditionalCatch(
                variableName = scope.firstChild.string,
                condition = visitExpr(ifne.single()),
                // dropLast for remove LEAVEWITH, GOTO
                block = processScope(block.asSequence().dropLast(2), block),
            )
        }

    private fun processSimpleCatch(scopeBlock: Pair<Node, Node>): IrSimpleCatch {
        val (scope, withOuter) = scopeBlock
        val (enter, with, leave) = withOuter.asTriple()
        val block = with.single()

        check(scope.type == Token.CATCH_SCOPE) { "invalid try" }
        check(withOuter.type == Token.BLOCK) { "invalid try" }
        check(enter.type == Token.ENTERWITH) { "invalid try" }
        check(with.type == Token.WITH) { "invalid try" }
        check(leave.type == Token.LEAVEWITH) { "invalid try" }
        check(block.type == Token.BLOCK) { "invalid try" }

        return IrSimpleCatch(
            variableName = scope.firstChild.string,
            // dropLast for remove LEAVEWITH, GOTO
            block = processScope(block.asSequence().dropLast(2), block),
        )
    }

    private fun processScope(node: Node): IrScope = processScope(node.asSequence(), node)

    private fun processScope(node: Sequence<Node>, parent: Node): IrScope {
        return IrScope(node.mapTo(mutableListOf()) { visitStatement(it, parent) }, (parent as? Scope)?.symbolTable
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
