package com.anatawa12.sai.stia.types

import com.anatawa12.sai.*
import com.anatawa12.sai.ast.*
import com.anatawa12.sai.stia.*
import com.anatawa12.sai.stia.JumpingInfo.Companion.jumpingInfo

abstract class AbstractVisitor {
    // region expressions
    protected abstract fun visitBitOr(node: Node, left: Node, right: Node)
    protected abstract fun visitBitXor(node: Node, left: Node, right: Node)
    protected abstract fun visitBitAnd(node: Node, left: Node, right: Node)
    protected abstract fun visitLsh(node: Node, left: Node, right: Node)
    protected abstract fun visitRsh(node: Node, left: Node, right: Node)
    protected abstract fun visitURsh(node: Node, left: Node, right: Node)
    protected abstract fun visitEq(node: Node, left: Node, right: Node)
    protected abstract fun visitNe(node: Node, left: Node, right: Node)
    protected abstract fun visitLt(node: Node, left: Node, right: Node)
    protected abstract fun visitLe(node: Node, left: Node, right: Node)
    protected abstract fun visitGt(node: Node, left: Node, right: Node)
    protected abstract fun visitGe(node: Node, left: Node, right: Node)
    protected abstract fun visitIn(node: Node, left: Node, right: Node)
    protected abstract fun visitShallowEq(node: Node, left: Node, right: Node)
    protected abstract fun visitShallowNe(node: Node, left: Node, right: Node)
    protected abstract fun visitInstanceOf(node: Node, left: Node, right: Node)
    protected abstract fun visitSub(node: Node, left: Node, right: Node)
    protected abstract fun visitMul(node: Node, left: Node, right: Node)
    protected abstract fun visitDiv(node: Node, left: Node, right: Node)
    protected abstract fun visitMod(node: Node, left: Node, right: Node)
    protected abstract fun visitAdd(node: Node, left: Node, right: Node)

    protected abstract fun visitNot(node: Node, operand: Node)
    protected abstract fun visitBitNot(node: Node, operand: Node)
    protected abstract fun visitPositive(node: Node, operand: Node)
    protected abstract fun visitNegative(node: Node, operand: Node)
    protected abstract fun visitTypeOf(node: Node, operand: Node)
    protected abstract fun visitVoid(node: Node, operand: Node)

    protected abstract fun visitGetProperty(node: Node, owner: Node, name: Node, isProperty: Boolean)
    protected abstract fun visitSetProperty(node: Node, owner: Node, name: Node, value: Node, isProperty: Boolean)

    protected abstract fun visitNewOrCall(node: Node, function: Node, args: List<Node>, isNewInstance: Boolean)

    protected abstract fun <T> visitLiteral(node: Node, type: CompileTimeConstantType<T>, value: T)

    protected abstract fun visitLocalLoad(node: Node)

    protected abstract fun visitCommaExpr(node: Node, exprs: List<Node>)

    protected abstract fun visitOr(node: Node, left: Node, right: Node)
    protected abstract fun visitAnd(node: Node, left: Node, right: Node)

    protected abstract fun visitPrefixIncrement(node: Node, operand: Node)
    protected abstract fun visitPrefixDecrement(node: Node, operand: Node)
    protected abstract fun visitPostfixIncrement(node: Node, operand: Node)
    protected abstract fun visitPostfixDecrement(node: Node, operand: Node)

    protected abstract fun visitPrefixIncrementName(node: Node, getting: Name, setting: Name)
    protected abstract fun visitPrefixDecrementName(node: Node, getting: Name, setting: Name)
    protected abstract fun visitPostfixIncrementName(node: Node, getting: Name, setting: Name)
    protected abstract fun visitPostfixDecrementName(node: Node, getting: Name, setting: Name)

    protected abstract fun visitConditionalOperator(node: Node, condition: Node, ifTrue: Node, ifFalse: Node)
    protected abstract fun visitSetVariable(node: Node, variable: Name, value: Node)
    protected abstract fun visitGetVariable(node: Name)

    protected abstract fun visitConvertException(node: Node, throwable: Node)
    // endregion expressions

    // region statements
    protected abstract fun visitReturn(node: Node, returns: Node?)
    protected abstract fun visitGoto(node: Jump)
    protected abstract fun visitJSR(node: Jump)
    protected abstract fun visitIfEQ(node: Jump, condition: Node)
    protected abstract fun visitIfNE(node: Jump, condition: Node)
    protected abstract fun visitInLoopJump(node: Jump, isContinue: Boolean)
    protected abstract fun visitSwitch(node: Jump, value: Node, cases: List<Pair<Jump, Node>>)
    protected abstract fun visitTarget(node: Node)

    protected abstract fun visitLabel(node: Node): ProcessResult
    //protected abstract fun visitWith(node: Node): ProcessResult
    protected abstract fun visitBlock(node: Node): ProcessResult
    protected abstract fun visitLoop(node: Node): ProcessResult
    protected abstract fun visitInternalLocalBlock(node: Node): ProcessResult
    protected abstract fun visitTryBlock(node: Jump): ProcessResult
    protected abstract fun visitFinally(node: Node)

    protected abstract fun visitVariableDefinition(node: Node, variables: List<Pair<Name, Node?>>, kind: Int)
    protected abstract fun visitThrow(node: Node, throws: Node)
    protected abstract fun visitRethrow(node: Node)
    protected abstract fun visitEmptyStatement(node: Node)
    protected abstract fun visitExprStatement(node: Node, expr: Node, isForReturn: Boolean)
    protected abstract fun visitFunctionStatement(node: Node)
    // endregion statements

    // region expression accepter
    private inline fun visitBinary(node: Node, visit: (Node, Node, Node) -> Unit) {
        val (left, right) = node.asPair()
        visit(node, left, right)
    }

    private inline fun visitUnary(node: Node, visit: (Node, Node) -> Unit) {
        val operand = node.single()
        visit(node, operand)
    }

    protected fun visitExpr(node: Node) {
        when (node.type) {
            Token.BITOR -> visitBinary(node, ::visitBitOr)
            Token.BITXOR -> visitBinary(node, ::visitBitXor)
            Token.BITAND -> visitBinary(node, ::visitBitAnd)
            Token.LSH -> visitBinary(node, ::visitLsh)
            Token.RSH -> visitBinary(node, ::visitRsh)
            Token.URSH -> visitBinary(node, ::visitURsh)
            Token.EQ -> visitBinary(node, ::visitEq)
            Token.NE -> visitBinary(node, ::visitNe)
            Token.LT -> visitBinary(node, ::visitLt)
            Token.LE -> visitBinary(node, ::visitLe)
            Token.GT -> visitBinary(node, ::visitGt)
            Token.GE -> visitBinary(node, ::visitGe)
            Token.IN -> visitBinary(node, ::visitIn)
            Token.SHEQ -> visitBinary(node, ::visitShallowEq)
            Token.SHNE -> visitBinary(node, ::visitShallowNe)
            Token.INSTANCEOF -> visitBinary(node, ::visitInstanceOf)
            Token.SUB -> visitBinary(node, ::visitSub)
            Token.MUL -> visitBinary(node, ::visitMul)
            Token.DIV -> visitBinary(node, ::visitDiv)
            Token.MOD -> visitBinary(node, ::visitMod)
            Token.ADD -> visitBinary(node, ::visitAdd)

            Token.NOT -> visitUnary(node, ::visitNot)
            Token.BITNOT -> visitUnary(node, ::visitBitNot)
            Token.POS -> visitUnary(node, ::visitPositive)
            Token.NEG -> visitUnary(node, ::visitNegative)
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
            Token.TYPEOF -> visitUnary(node, ::visitTypeOf)
            Token.GETPROP,
            Token.GETELEM,
            -> {
                val (owner, name) = node.asPair()
                visitGetProperty(node, owner, name, node.type == Token.GETPROP)
            }
            Token.SETPROP,
            Token.SETELEM,
            -> {
                val (owner, name, value) = node.asTriple()
                visitSetProperty(node, owner, name, value, node.type == Token.SETPROP)
            }
            Token.NEW,
            Token.CALL,
            -> {
                val isNewInstance = node.type == Token.NEW
                val function = node.first()
                val args = node.drop(1)
                visitNewOrCall(node, function, args, isNewInstance)
            }
            Token.THIS,
            -> unsupported("THIS")
            // literal
            Token.NUMBER -> visitLiteral(node, NumberType, node.double)
            Token.STRING -> visitLiteral(node, StringType, node.string)
            Token.NULL -> visitLiteral(node, NullType, null)
            Token.FALSE -> visitLiteral(node, BooleanType, false)
            Token.TRUE -> visitLiteral(node, BooleanType, true)
            // regexp literal
            Token.REGEXP -> visitLiteral(node, RegexpType, node as RegExpLiteral)
            Token.LOCAL_LOAD -> visitLocalLoad(node)
            // array literal
            Token.ARRAYLIT -> unsupported("array literal")
            // object literal
            Token.OBJECTLIT -> unsupported("object literal")
            // void expr;
            Token.VOID -> visitUnary(node, ::visitVoid)
            // typeof name: this is Name
            Token.TYPEOFNAME,
            -> unsupported("TYPEOFNAME")
            Token.USE_STACK, // use the value on the stack,
            -> unsupported("USE_STACK")
            Token.SETPROP_OP, // a.b op= c
            Token.SETELEM_OP, // a.[b] op= c
            -> unsupported("OPERATOR_ASSIGN")
            // expr, expr, expr....
            Token.COMMA -> visitCommaExpr(node, node.toList())
            Token.OR -> visitBinary(node, ::visitOr)
            Token.AND -> visitBinary(node, ::visitAnd)
            Token.INC, // ++ expr or expr ++
            Token.DEC, // -- expr or expr --
            -> {
                val prop = node.getExistingIntProp(Node.INCRDECR_PROP)
                if (prop and Node.DECR_FLAG == 0)
                    if (prop and Node.POST_FLAG != 0)
                        visitUnary(node, ::visitPostfixIncrement)
                    else
                        visitUnary(node, ::visitPrefixIncrement)
                else
                    if (prop and Node.POST_FLAG != 0)
                        visitUnary(node, ::visitPostfixDecrement)
                    else
                        visitUnary(node, ::visitPrefixDecrement)
            }
            Token.INC_DEC_NAME, // -- expr or expr --
            -> {
                val prop = node.getExistingIntProp(Node.INCRDECR_PROP)
                val (left, right) = node.asPair()
                left as Name
                right as Name
                if (prop and Node.DECR_FLAG == 0)
                    if (prop and Node.POST_FLAG != 0)
                        visitPostfixIncrementName(node, left, right)
                    else
                        visitPrefixIncrementName(node, left, right)
                else
                    if (prop and Node.POST_FLAG != 0)
                        visitPostfixDecrementName(node, left, right)
                    else
                        visitPrefixDecrementName(node, left, right)
            }

            Token.HOOK, // condition ? then : else
            -> {
                val (condition, ifTrue, ifFalse) = node.asTriple()
                visitConditionalOperator(node, condition, ifTrue, ifFalse)
            }

            Token.SETNAME -> {
                val (name, variable) = node.asPair()
                visitSetVariable(node, name as Name, variable)
            }
            Token.NAME -> visitGetVariable(node as Name)
            Token.BINDNAME -> unsupported("BINDNAME")

            // reference to this function
            Token.THISFN -> unsupported("THISFN")

            // literal or root definition
            Token.FUNCTION -> unsupported("FUNCTION")

            Token.CONVERT_EXCEPTION,
            -> {
                val convertFrom = node.single()
                visitConvertException(node, convertFrom)
            }

            Token.RETURN, // void
            Token.GOTO,
            Token.JSR,
            Token.IFEQ,
            Token.IFNE,
            Token.BREAK,
            Token.CONTINUE,
            Token.SWITCH,
            Token.TARGET,
            Token.LABEL, // block
                // Token.WITH, // unsuppoted
            Token.BLOCK, //Token.ARRAYCOMP, unsupported
            Token.LOCAL_BLOCK,
            Token.LOOP,
            Token.CASE, // case label(?)
            Token.TRY, // try {} catch {} finally {}
            Token.FINALLY,
            Token.VAR,
            Token.LET,
            Token.CONST,
            Token.THROW,
            Token.RETHROW,
            Token.EMPTY, // ; // empty statement
            Token.EXPR_VOID, // expression statement
            Token.EXPR_RESULT, // expression statement for root script
            -> unsupported("statements: ${node.type}")
            Token.ENTERWITH,
            Token.LEAVEWITH,
            Token.WITH,
            Token.WITHEXPR,
            -> unsupported("with")
            Token.DEBUGGER,
            -> unsupported("debugger")
            Token.METHOD,
            -> unsupported("method")
            Token.SCRIPT,
            -> unsupported("root script is not supported")
            Token.CATCH_SCOPE,
            -> unsupported("catch scope")
            Token.ENUM_INIT_KEYS,
            Token.ENUM_INIT_VALUES,
            Token.ENUM_INIT_ARRAY,
            Token.ENUM_INIT_VALUES_IN_ORDER,
            Token.ENUM_NEXT,
            Token.ENUM_ID,
            -> unsupported("enumeration loops")
            Token.GET_REF,
            Token.SET_REF,
            Token.DEL_REF,
            Token.REF_CALL,
            Token.REF_SPECIAL,
            -> unsupported("references")
            Token.YIELD,
            Token.YIELD_STAR,
            -> unsupported("generators")
            Token.DEFAULTNAMESPACE,
            Token.ESCXMLATTR,
            Token.ESCXMLTEXT,
            Token.REF_MEMBER,
            Token.REF_NS_MEMBER,
            Token.REF_NAME,
            Token.REF_NS_NAME,
            -> unsupported("xml")
            Token.SET_REF_OP,
            -> unsupported("reference")
            Token.DOTQUERY,
            -> unsupported("xml")
            Token.GET,
            Token.SET,
            -> unsupported("get and set in object literal")
            Token.SETCONST,
            Token.SETCONSTVAR,
            -> unsupported("deconstructing const assign")
            Token.ARRAYCOMP,
                // see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Operators/Array_comprehensions
            -> unsupported("array comprehension expression")
            Token.LETEXPR,
            -> unsupported("deconstructing const assign")
            Token.STRICT_SETNAME,
            Token.GETVAR,
            Token.SETVAR,
            Token.RETURN_RESULT,
            Token.TO_OBJECT, // double to object wrapping
            Token.TO_DOUBLE, // similar to Number(expr)
            -> unsupported("transformed insn")
            //Token.SEMI,
            //Token.LB,
            //Token.RB,
            //Token.LC,
            //Token.RC,
            //Token.LP,
            //Token.RP,
            //Token.ASSIGN,
            //Token.ASSIGN_BITOR,
            //Token.ASSIGN_BITXOR,
            //Token.ASSIGN_BITAND,
            //Token.ASSIGN_LSH,
            //Token.ASSIGN_RSH,
            //Token.ASSIGN_URSH,
            //Token.ASSIGN_ADD,
            //Token.ASSIGN_SUB,
            //Token.ASSIGN_MUL,
            //Token.ASSIGN_DIV,
            //Token.ASSIGN_MOD,
            //Token.COLON,
            //Token.DOT,
            //Token.EXPORT,
            //Token.IMPORT,
            //Token.IF,
            //Token.ELSE,
            //Token.DEFAULT,
            //Token.WHILE,
            //Token.DO,
            //Token.FOR,
            //Token.CATCH,
            //Token.RESERVED,
            //Token.DOTDOT,
            //Token.COLONCOLON,
            //Token.XML,
            //Token.XMLATTR,
            //Token.XMLEND,
            //Token.COMMENT,
            //Token.GENEXPR,
            //Token.ARROW,
            else
            -> unsupported("unsupported token: ${node.type}")
        }
    }
    // endregion expression accepter

    sealed class ProcessResult {
        object Continue : ProcessResult()
        object Jumped : ProcessResult()
        companion object {
            fun reachable(reachable: Reachable) = if (reachable.reachable) Continue else Jumped
        }
    }

    protected fun visitStatement(node: Node): ProcessResult {
        when (node.type) {
            Token.RETURN -> {
                visitReturn(node, node.firstChild.nullable())
                return ProcessResult.Jumped
            }

            // jumping instructions
            Token.GOTO -> {
                visitGoto(node as Jump)
                return ProcessResult.Jumped
            }
            Token.JSR -> {
                visitJSR(node as Jump)
                return ProcessResult.reachable(node.jumpingInfo.mayContinue)
            }
            // jump if true
            Token.IFEQ -> {
                visitIfEQ(node as Jump, node.single())
                return ProcessResult.reachable(node.jumpingInfo.mayContinue)
            }
            // jump if false
            Token.IFNE -> {
                visitIfNE(node as Jump, node.single())
                return ProcessResult.reachable(node.jumpingInfo.mayContinue)
            }
            Token.BREAK, Token.CONTINUE -> {
                visitInLoopJump(node as Jump, node.type == Token.CONTINUE)
                return ProcessResult.Jumped
            }

            // switch statement
            Token.SWITCH -> {
                visitSwitch(node as Jump, node.first(), node.drop(1).map { it as Jump to it.single() })
                return ProcessResult.reachable(node.jumpingInfo.mayContinue)
            }

            Token.TARGET -> {
                visitTarget(node)
                return ProcessResult.Continue
            }

            // statements or expressions block
            // labeled block
            Token.LABEL -> return visitLabel(node)
            //Token.WITH -> return visitWith(node)

            // scope block
            Token.BLOCK -> return visitBlock(node)
            Token.LOOP -> return visitLoop(node)

            // contextual local variable (hidden)
            Token.LOCAL_BLOCK -> return visitInternalLocalBlock(node)

            Token.TRY -> return visitTryBlock(node as Jump)
            Token.FINALLY -> {
                visitFinally(node)
                return ProcessResult.Jumped
            }

            Token.VAR, Token.LET, Token.CONST -> {
                visitVariableDefinition(node, node.map { it as Name to it.singleOrNull() }, node.type)
                return ProcessResult.Continue
            }

            Token.THROW -> {
                visitThrow(node, node.single())
                return ProcessResult.Jumped
            }
            Token.RETHROW -> {
                visitRethrow(node)
                return ProcessResult.Jumped
            }
            // ; // empty statement
            Token.EMPTY -> {
                visitEmptyStatement(node)
                return ProcessResult.Continue
            }

            Token.EXPR_VOID, Token.EXPR_RESULT -> {
                visitExprStatement(node, node.single(), node.type == Token.EXPR_RESULT)
                return ProcessResult.Continue
            }

            // literal or root definition
            Token.FUNCTION -> {
                visitFunctionStatement(node)
                return ProcessResult.Continue
            }

            Token.CASE,
            -> unsupported("case")
            Token.BINDNAME,
            Token.BITOR,
            Token.BITXOR,
            Token.BITAND,
            Token.LSH,
            Token.RSH,
            Token.URSH,
            Token.EQ,
            Token.NE,
            Token.LT,
            Token.LE,
            Token.GT,
            Token.GE,
            Token.IN,
            Token.INSTANCEOF,
            Token.ADD,
            Token.SUB,
            Token.MUL,
            Token.DIV,
            Token.MOD,
            Token.NOT,
            Token.BITNOT,
            Token.POS, // + expr
            Token.NEG, // - expr
            Token.SHEQ, // shallow ===
            Token.SHNE, // shallow !==
            Token.NEW,
            Token.DELPROP, // delete expr
            Token.TYPEOF, // typeof expr
            Token.GETPROP,
            Token.GETPROPNOWARN,
            Token.GETELEM,
            Token.SETPROP,
            Token.SETELEM,
            Token.CALL,
            Token.NUMBER, // literal
            Token.STRING,
            Token.NULL,
            Token.THIS,
            Token.FALSE,
            Token.TRUE,
            Token.REGEXP, // regexp literal
            Token.LOCAL_LOAD,
            Token.ARRAYLIT, // array literal
            Token.OBJECTLIT, // object literal
            Token.VOID, // void expr;
            Token.TYPEOFNAME, // typeof expr
            Token.USE_STACK, // use the value on the stack,
            Token.SETPROP_OP, // a.b op= c
            Token.SETELEM_OP, // a.[b] op= c
            Token.COMMA, // expr, expr, expr....
            Token.OR, // ||
            Token.AND, // &&
            Token.INC, // ++ expr or expr ++
            Token.DEC, // -- expr or expr --
            Token.HOOK, // condition ? then : else
            Token.SETNAME,
            Token.NAME,
            Token.THISFN, // reference to this function
            -> unsupported("expression: ${node.type}")
            Token.ENTERWITH,
            Token.LEAVEWITH,
            Token.WITH,
            Token.WITHEXPR,
            -> unsupported("with")
            Token.DEBUGGER,
            -> unsupported("debugger")
            Token.METHOD,
            -> unsupported("method")
            Token.CONVERT_EXCEPTION,
            -> unsupported("debugger")
            Token.SCRIPT,
            -> unsupported("root script is not supported")
            Token.CATCH_SCOPE,
            -> unsupported("catch scope")
            Token.ENUM_INIT_KEYS,
            Token.ENUM_INIT_VALUES,
            Token.ENUM_INIT_ARRAY,
            Token.ENUM_INIT_VALUES_IN_ORDER,
            Token.ENUM_NEXT,
            Token.ENUM_ID,
            -> unsupported("enumeration loops")
            Token.GET_REF,
            Token.SET_REF,
            Token.DEL_REF,
            Token.REF_CALL,
            Token.REF_SPECIAL,
            -> unsupported("references")
            Token.YIELD,
            Token.YIELD_STAR,
            -> unsupported("generators")
            Token.DEFAULTNAMESPACE,
            Token.ESCXMLATTR,
            Token.ESCXMLTEXT,
            Token.REF_MEMBER,
            Token.REF_NS_MEMBER,
            Token.REF_NAME,
            Token.REF_NS_NAME,
            -> unsupported("xml")
            Token.SET_REF_OP,
            -> unsupported("reference")
            Token.DOTQUERY,
            -> unsupported("xml")
            Token.GET,
            Token.SET,
            -> unsupported("get and set in object literal")
            Token.SETCONST,
            Token.SETCONSTVAR,
            -> unsupported("deconstructing const assign")
            Token.ARRAYCOMP,
                // see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Operators/Array_comprehensions
            -> unsupported("array comprehension expression")
            Token.LETEXPR,
            -> unsupported("deconstructing const assign")
            Token.STRICT_SETNAME,
            Token.GETVAR,
            Token.SETVAR,
            Token.RETURN_RESULT,
            Token.TO_OBJECT, // double to object wrapping
            Token.TO_DOUBLE, // similar to Number(expr)
            -> unsupported("transformed insn")
            //Token.SEMI,
            //Token.LB,
            //Token.RB,
            //Token.LC,
            //Token.RC,
            //Token.LP,
            //Token.RP,
            //Token.ASSIGN,
            //Token.ASSIGN_BITOR,
            //Token.ASSIGN_BITXOR,
            //Token.ASSIGN_BITAND,
            //Token.ASSIGN_LSH,
            //Token.ASSIGN_RSH,
            //Token.ASSIGN_URSH,
            //Token.ASSIGN_ADD,
            //Token.ASSIGN_SUB,
            //Token.ASSIGN_MUL,
            //Token.ASSIGN_DIV,
            //Token.ASSIGN_MOD,
            //Token.COLON,
            //Token.DOT,
            //Token.EXPORT,
            //Token.IMPORT,
            //Token.IF,
            //Token.ELSE,
            //Token.DEFAULT,
            //Token.WHILE,
            //Token.DO,
            //Token.FOR,
            //Token.CATCH,
            //Token.RESERVED,
            //Token.DOTDOT,
            //Token.COLONCOLON,
            //Token.XML,
            //Token.XMLATTR,
            //Token.XMLEND,
            //Token.COMMENT,
            //Token.GENEXPR,
            //Token.ARROW,
            else
            -> unsupported("unsupported token: ${node.type}")
        }
    }

    protected fun statementsBlock(node: Node): ProcessResult {
        var isReturned = false
        for (child in node) {
            if (isReturned && child.type != Token.TARGET)
                continue
            isReturned = false
            when (visitStatement(child)) {
                ProcessResult.Continue -> {
                }
                ProcessResult.Jumped -> {
                    isReturned = true
                }
            }
        }

        if (isReturned) return ProcessResult.Jumped
        return ProcessResult.Continue
    }
}
