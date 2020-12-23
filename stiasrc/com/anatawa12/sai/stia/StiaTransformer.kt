package com.anatawa12.sai.stia

import com.anatawa12.sai.Kit
import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.*


class StiaTransformer {
    fun transform(node: ScriptNode) {
        transform(node, node)
    }

    private fun transform(node: Node, top: ScriptNode) {
        for (child in node) {
            when (child.type) {
                Token.TRY -> {
                    transformTry(child as Jump, node, top)
                    transform(child, top)
                }
                Token.INC, Token.DEC -> {
                    val expr = child.single()
                    if (expr.type == Token.NAME) {
                        expr as Name
                        child.type = Token.INC_DEC_NAME
                        child.addChildToBack(Name().apply {
                            identifier = expr.identifier
                        })
                    }
                    transform(child, top)
                }
                else -> transform(child, top)
            }
        }
        if (node is ScriptNode) {
            for (function in node.functions) {
                transform(function, function)
            }
        }
    }

    /**
     * transform 'try' statement to be without 'with' statement.
     * the IRFactory uses 'with' for catch-cause-scope but I stia will not support 'with' so convert to 'let' declaration.
     */
    private fun transformTry(tryStat: Jump, localBlock: Node, top: ScriptNode) {
        if (tryStat.type != Token.TRY) Kit.codeBug()
        if (localBlock.type != Token.LOCAL_BLOCK)
            unsupported("parent of TRY is not LOCAL_BLOCK")
        if (localBlock.singleOrNull() != tryStat)
            unsupported("child of LOCAL_BLOCK must be single TRY block")

        val catchTarget = tryStat.target.nullable() ?: return

        val tryBlock = tryStat.firstChild
        val gotoEndCatch = tryBlock.next
        val catchTarget1 = gotoEndCatch.next
        if (gotoEndCatch !is Jump) unsupported("TRY format")
        if (gotoEndCatch.type != Token.GOTO) unsupported("TRY format")
        val endCatch = gotoEndCatch.target
        if (catchTarget1 != catchTarget) unsupported("TRY format")
        var cur = catchTarget1
        while (cur.next?.type == Token.LOCAL_BLOCK) {
            val catchLocalBlock = cur.next
            if (catchLocalBlock.type != Token.LOCAL_BLOCK) unsupported("TRY format")
            cur = cur.next
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
            val newBlock = Scope(Token.BLOCK)
            newBlock.top = top
            newBlock.putSymbol(Symbol(Token.LET, name.string))

            val letExpr = VariableDeclaration().apply {
                addChildToBack(Name(-1, name.string).apply {
                    addChildToBack(Node(Token.CONVERT_EXCEPTION).apply {
                        addChildToBack(Node(Token.LOCAL_LOAD).apply {
                            putProp(Node.LOCAL_BLOCK_PROP, localBlock)
                        })
                    })
                })
            }

            newBlock.addChildToBack(letExpr)
            newBlock.addChildToBack(withBody)

            tryStat.replaceChild(catchLocalBlock, newBlock)
        }
    }

    private fun unsupported(message: String): Nothing{
        TODO("unsupported: $message")
    }
}
