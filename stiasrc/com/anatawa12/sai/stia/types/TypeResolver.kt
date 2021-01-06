package com.anatawa12.sai.stia.types

import com.anatawa12.sai.*
import com.anatawa12.sai.ScriptRuntime as SR
import com.anatawa12.sai.ast.*
import com.anatawa12.sai.linker.ClassList
import com.anatawa12.sai.regexp.NativeRegExp
import com.anatawa12.sai.stia.*
import java.lang.reflect.Method

class TypeResolver : AbstractVisitor() {
    fun process(func: FunctionNode, signature: Method?) {
        if (signature != null) {
            val paramTypes = signature.parameterTypes
            for ((i, varId) in func.scopeInfo.parameters.withIndex()) {
                (varId.valueInfo as SSAValueInfo)
                    .exactly(ResolvingType.byClass(paramTypes.getOrNull(i)))
            }
        }
        for (node in func) {
            visitStatement(node)
        }
    }

    fun process(script: ScriptNode) {
        for (node in script) {
            visitStatement(node)
        }
    }

    private fun <T1 : Any, T2 : Any> onValue(
        type1: CompileTimeConstantType<T1>, info1: ValueInfo,
        type2: CompileTimeConstantType<T2>, info2: ValueInfo,
        block: (T1, T2) -> Unit
    ) {
        info1.getValueOrNone(type1).map { value1 ->
            info2.getValueOrNone(type2).map { value2 ->
                block(value1, value2)
                return
            }
        }
        val callback: (Any) -> Unit = callback@{
            block(
                info1.getValueOrNone(type1).orElse { return@callback },
                info2.getValueOrNone(type2).orElse { return@callback },
            )
        }
        info1.onValue(type1, callback)
        info2.onValue(type2, callback)
    }

    private fun visitExprs(vararg nodes: Node) {
        for (node in nodes) {
            visitExpr(node)
        }
    }

    private fun onValueTypes(elements: List<Node>, block: (List<ResolvingType>) -> Unit) {
        onValueTypes(elements.asSequence().map { it.valueInfo }, elements.size, block)
    }

    private fun onValueTypes(elements: Sequence<ValueInfo>, size: Int, block: (List<ResolvingType>) -> Unit) {
        class Caller(val iterator: Iterator<ValueInfo>, val expectedCount: Int, val block: (List<ResolvingType>) -> Unit) : (ValueInfo, ResolvedTypeKind, ResolvingType) -> Unit {
            val list = mutableListOf<ResolvingType>()

            override fun invoke(
                self: ValueInfo,
                kind: ResolvedTypeKind,
                type: ResolvingType
            ) {
                list += type
                run()
            }

            fun run() {
                if (iterator.hasNext()) {
                    iterator.next().onResolvedType(ResolvedTypeKind.ExactlyType, this)
                } else {
                    check(list.size == expectedCount)
                    this.block(list)
                }
            }
        }
        Caller(elements.iterator(), size, block).run()
    }

    private fun visitBinaryNumberOperator(node: Node, operation: (Double, Double) -> Double) {
        val (left, right) = node.asPair()
        visitExprs(left, right)
        val leftInfo = left.valueInfo
        val rightInfo = right.valueInfo
        val nodeInfo = SSAValueInfo().also { node.valueInfo = it }
        leftInfo.expect(NumberType)
        rightInfo.expect(NumberType)
        nodeInfo.exactly(NumberType)
        onValue(
            NumberType, leftInfo,
            NumberType, rightInfo,
        ) { leftValue, rightValue ->
            nodeInfo.exactlyValue(operation(leftValue, rightValue))
        }
    }

    private fun visitIntBinaryNumberOperator(node: Node, operation: (Int, Int) -> Int) {
        visitBinaryNumberOperator(node) { leftValue, rightValue ->
            operation(SR.toInt32(leftValue), SR.toInt32(rightValue)).toDouble()
        }
    }

    // region expressions
    override fun visitBitOr(node: Node, left: Node, right: Node) = visitIntBinaryNumberOperator(node, Int::or)
    override fun visitBitXor(node: Node, left: Node, right: Node) = visitIntBinaryNumberOperator(node, Int::xor)
    override fun visitBitAnd(node: Node, left: Node, right: Node) = visitIntBinaryNumberOperator(node, Int::and)
    override fun visitLsh(node: Node, left: Node, right: Node) = visitIntBinaryNumberOperator(node, Int::shl)
    override fun visitRsh(node: Node, left: Node, right: Node) = visitIntBinaryNumberOperator(node, Int::shr)
    override fun visitURsh(node: Node, left: Node, right: Node) =
        visitBinaryNumberOperator(node) { a, b -> (SR.toUint32(a) ushr SR.toInt32(b).and(31)).toDouble() }

    private fun visitBinaryBooleanExpr(node: Node, left: Node, right: Node) {
        visitExprs(left, right)
        // TODO: statically value
        node.valueInfo = SSAValueInfo().also {
            it.exactly(BooleanType)
        }
    }

    override fun visitEq(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitNe(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitLt(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitLe(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitGt(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitGe(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitIn(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitShallowEq(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitShallowNe(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)
    override fun visitInstanceOf(node: Node, left: Node, right: Node) = visitBinaryBooleanExpr(node, left, right)

    override fun visitSub(node: Node, left: Node, right: Node) = visitBinaryNumberOperator(node, Double::minus)
    override fun visitMul(node: Node, left: Node, right: Node) = visitBinaryNumberOperator(node, Double::times)
    override fun visitDiv(node: Node, left: Node, right: Node) = visitBinaryNumberOperator(node, Double::div)
    override fun visitMod(node: Node, left: Node, right: Node) = visitBinaryNumberOperator(node, Double::rem)

    override fun visitAdd(node: Node, left: Node, right: Node) {
        visitExprs(left, right)
        val leftValueInfo = left.valueInfo
        val rightValueInfo = right.valueInfo
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            // TODO: statically value
            // TODO: select number or string if possible
            leftValueInfo.onResolvedType(ResolvedTypeKind.ExactlyType) { _, leftType ->
                rightValueInfo.onResolvedType(ResolvedTypeKind.ExactlyType) { _, rightType ->
                    if (leftType == NumberType && rightType == NumberType) {
                        valueInfo.exactly(NumberType)
                    } else if (leftType == StringType || rightType == StringType) {
                        valueInfo.exactly(StringType)
                    }
                }
            }
        }
    }

    override fun visitNot(node: Node, operand: Node) {
        visitExprs(operand)
        operand.valueInfo.expect(BooleanType)
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactly(BooleanType)
            operand.valueInfo.onValue(BooleanType) {
                valueInfo.exactlyValue(it.not())
            }
        }
    }

    override fun visitBitNot(node: Node, operand: Node) {
        visitExprs(operand)
        operand.valueInfo.expect(NumberType)
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactly(NumberType)
            operand.valueInfo.onValue(NumberType) {
                valueInfo.exactlyValue((SR.toInt32(it) xor -1).toDouble())
            }
        }
    }

    override fun visitPositive(node: Node, operand: Node) {
        visitExprs(operand)
        operand.valueInfo.expect(NumberType)
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactly(NumberType)
            operand.valueInfo.onValue(NumberType) {
                valueInfo.exactlyValue(it)
            }
        }
    }

    override fun visitNegative(node: Node, operand: Node) {
        visitExprs(operand)
        operand.valueInfo.expect(NumberType)
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactly(NumberType)
            operand.valueInfo.onValue(NumberType) {
                valueInfo.exactlyValue(it.unaryMinus())
            }
        }
    }

    override fun visitTypeOf(node: Node, operand: Node) {
        visitExprs(operand)
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactly(StringType)
            operand.valueInfo.onResolvedType(ResolvedTypeKind.ExactlyType) { _, type ->
                when (type) {
                    BooleanType -> valueInfo.exactlyValue("boolean")
                    NumberType -> valueInfo.exactlyValue("number")
                    StringType -> valueInfo.exactlyValue("string")
                    NullType -> valueInfo.exactlyValue("object")
                    UndefinedType -> valueInfo.exactlyValue("undefined")
                    RegexpType -> valueInfo.exactlyValue("object")
                    is InternalValueType -> unsupported("internal value as javascript value")
                    is JavaValueType -> {}//valueInfo.exactlyValue("object") // TODO: check JSValue and Scriptable implementation
                    is JavaClassType -> valueInfo.exactlyValue("object")
                    is JavaPackageType -> valueInfo.exactlyValue("object")
                }
            }
        }
    }

    override fun visitVoid(node: Node, operand: Node) {
        visitExpr(operand)
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactlyValue(Undefined.instance)
        }
    }

    private fun resolveGetProp(valueInfo: SSAValueInfo, ownerType: ResolvingType, name: String) {
        // TODO
        when (ownerType) {
            is JavaValueType -> {
                val fieldType = SaiAccessor.resolveField(ownerType.type, name, false)
                    ?: return
                valueInfo.exactly(ResolvingType.byClass(fieldType))
            }
            is JavaClassType -> {
                val fieldType = SaiAccessor.resolveField(ownerType.type, name, true)
                    ?: return
                valueInfo.exactly(ResolvingType.byClass(fieldType))
            }
            else -> {
            }
        }
    }

    override fun visitGetProperty(node: Node, owner: Node, name: Node, isProperty: Boolean) {
        visitExprs(owner, name)
        val valueInfo = SSAValueInfo().also { node.valueInfo = it }
        name.valueInfo.onValue(StringType) { nameStr ->
            owner.valueInfo.onResolvedType(ResolvedTypeKind.ExactlyType) { _, ownerType ->
                resolveGetProp(valueInfo, ownerType, nameStr)
            }
        }
        // TODO: statically value
        // TODO: statically type
        // maybe name expected as number or string
    }

    override fun visitSetProperty(node: Node, owner: Node, name: Node, value: Node, isProperty: Boolean) {
        visitExprs(owner, name, value)
        // TODO: statically value
        // TODO: statically type
        // maybe name expected as number or string
        // TODO: node.type = value.type
    }

    fun processJavaClass(valueInfo: SSAValueInfo, type: Class<*>, name: String, args: List<Node>, isStatic: Boolean) {
        val dynamicMethod = SaiAccessor.resolveMethod(type, name, isStatic)
            ?: return
        // TODO: use field resolve
        val overloads = dynamicMethod.getOverloads(args.size)
        if (overloads.methodsCount() == 1) {
            val method = overloads.theOnlyMethod()
            val returnType = method.returnType()
            valueInfo.exactly(ResolvingType.byClass(returnType))
        } else {
            onValueTypes(args) { list ->
                val classList = ClassList(list.mapToArray {
                    when (it) {
                        BooleanType -> Boolean::class.java
                        NumberType -> Number::class.java
                        StringType -> String::class.java
                        NullType -> ClassList.NULL_CLASS
                        UndefinedType -> Undefined::class.java
                        RegexpType -> NativeRegExp::class.java
                        is InternalValueType -> error("can't pass internal value to function")
                        is JavaValueType -> it.type
                        is JavaClassType -> Class::class.java
                        is JavaPackageType -> NativeJavaPackage::class.java
                    }
                })
                val method = kotlin.runCatching { dynamicMethod.getInvocation(classList) }
                    .getOrNull()
                    ?: return@onValueTypes
                val returnType = method.returnType()
                valueInfo.exactly(ResolvingType.byClass(returnType))
            }
        }
    }

    override fun visitNewOrCall(node: Node, function: Node, args: List<Node>, isNewInstance: Boolean) {
        val valueInfo = SSAValueInfo().also { node.valueInfo = it }
        //TODO("call expr")
        when (function.type) {
            Token.GETPROP,
            Token.GETELEM-> {
                val (owner, index) = function.asPair()
                visitExpr(owner)
                visitExpr(index)
                args.forEach(::visitExpr)
                index.valueInfo.onValue(StringType) { nameStr ->
                    owner.valueInfo.onResolvedType(ResolvedTypeKind.ExactlyType) { _, type ->
                        when (type) {
                            is JavaValueType -> {
                                processJavaClass(valueInfo, type.type, nameStr, args, false)
                            }
                            is JavaClassType -> {
                                processJavaClass(valueInfo, type.type, nameStr, args, true)
                            }
                            else -> {
                                resolveGetProp(valueInfo, type, nameStr)
                            }
                        }
                    }
                }
            }
            else -> {
                visitExpr(function)
                args.forEach(::visitExpr)
            }
        }
    }

    override fun <T> visitLiteral(node: Node, type: CompileTimeConstantType<T>, value: T) {
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactlyValue(value)
        }
    }

    override fun visitLocalLoad(node: Node) {
        node.realLocalVarId.valueInfo.assignTo(node.valueInfo)
    }

    override fun visitCommaExpr(node: Node, exprs: List<Node>) {
        for (expr in exprs) {
            visitExpr(expr)
        }
        exprs.last().valueInfo.assignTo(node.valueInfo)
    }

    override fun visitOr(node: Node, left: Node, right: Node) {
        visitExprs(left, right)
        //TODO: node.valueInfo.type = computeOrType(left.valueInfo.type, right.valueInfo.type)
        left.valueInfo.onValue(BooleanType) {
            if (it) {
                left.valueInfo.assignTo(node.valueInfo)
            } else {
                right.valueInfo.assignTo(node.valueInfo)
            }
        }
    }

    override fun visitAnd(node: Node, left: Node, right: Node) {
        visitExprs(left, right)
        //TODO: node.valueInfo.type = computeOrType(left.valueInfo.type, right.valueInfo.type)
        left.valueInfo.onValue(BooleanType) {
            if (it) {
                right.valueInfo.assignTo(node.valueInfo)
            } else {
                left.valueInfo.assignTo(node.valueInfo)
            }
        }
    }

    private fun visitInDecrement(node: Node, operand: Node, isIncrement: Boolean, isPrefix: Boolean) {
        visitExprs(operand)
        operand.valueInfo.expect(NumberType)
        node.valueInfo = SSAValueInfo().also { valueInfo ->
            valueInfo.exactly(NumberType)


            operand.valueInfo.onValue(NumberType) { value ->
                val modified = if (isIncrement) value + 1 else value - 1
                val result = if (isPrefix) modified else value
                // TODO: operand = operand + 1
                valueInfo.exactlyValue(result)
            }
        }
    }

    override fun visitPrefixIncrement(node: Node, operand: Node) = visitInDecrement(node, operand, isIncrement = true, isPrefix = true)
    override fun visitPrefixDecrement(node: Node, operand: Node) = visitInDecrement(node, operand, isIncrement = false, isPrefix = true)
    override fun visitPostfixIncrement(node: Node, operand: Node) = visitInDecrement(node, operand, isIncrement = true, isPrefix = false)
    override fun visitPostfixDecrement(node: Node, operand: Node) = visitInDecrement(node, operand, isIncrement = false, isPrefix = false)

    private fun visitInDecrementName(node: Node, getting: Name, setting: Name, isIncrement: Boolean, isPrefix: Boolean) {
        visitExprs(getting)
        getting.valueInfo.expect(NumberType)
        val settingValueInfo = setting.valueInfo as SSAValueInfo
        val nodeValueInfo = SSAValueInfo().also { node.valueInfo = it }

        settingValueInfo.exactly(NumberType)
        nodeValueInfo.exactly(NumberType)

        getting.valueInfo.onValue(NumberType) { value ->
            val modified = if (isIncrement) value + 1 else value - 1
            val result = if (isPrefix) modified else value
            nodeValueInfo.exactlyValue(result)
            settingValueInfo.exactlyValue(modified)
        }
    }

    override fun visitPrefixIncrementName(node: Node, getting: Name, setting: Name) = visitInDecrementName(node, getting, setting, isIncrement = true, isPrefix = true)
    override fun visitPrefixDecrementName(node: Node, getting: Name, setting: Name) = visitInDecrementName(node, getting, setting, isIncrement = false, isPrefix = true)
    override fun visitPostfixIncrementName(node: Node, getting: Name, setting: Name) = visitInDecrementName(node, getting, setting, isIncrement = true, isPrefix = false)
    override fun visitPostfixDecrementName(node: Node, getting: Name, setting: Name) = visitInDecrementName(node, getting, setting, isIncrement = false, isPrefix = false)

    override fun visitConditionalOperator(node: Node, condition: Node, ifTrue: Node, ifFalse: Node) {
        visitExprs(condition, ifTrue, ifFalse)

        // TODO: node.valueInfo.type = computeOrType(ifTrue.valueInfo.type, ifFalse.valueInfo.type)

        condition.valueInfo.expect(BooleanType)
        condition.valueInfo.onValue(BooleanType) {
            if (it) {
                ifTrue.valueInfo.assignTo(node.valueInfo)
            } else {
                ifFalse.valueInfo.assignTo(node.valueInfo)
            }
        }
    }

    override fun visitSetVariable(node: Node, variable: Name, value: Node) {
        visitExpr(value)
        node.valueInfo = SSAValueInfo()
        value.valueInfo.assignTo(to = variable.valueInfo)
        value.valueInfo.assignTo(to = node.valueInfo)
    }

    override fun visitGetVariable(node: Name) {
        //nop
    }

    override fun visitConvertException(node: Node, throwable: Node) {
        val convertFrom = node.single()
        visitExpr(convertFrom)
        convertFrom.valueInfo.expect(InternalValueType(Throwable::class.java))
        // this expression returns any type.
    }

    // endregion

    //region statements
    override fun visitReturn(node: Node, returns: Node?) {
        if (returns != null)
            visitExpr(returns)
    }

    override fun visitGoto(node: Jump) {
        // nop
    }

    override fun visitJSR(node: Jump) {
        processAsTarget(node)
    }

    override fun visitIfEQ(node: Jump, condition: Node) {
        visitExpr(condition)
        condition.valueInfo.expect(BooleanType)
        condition.valueInfo.onValue(BooleanType) { value ->
            if (value) { // jump so remove continuous
                removeContinuousJump(node)
            } else { // continuous so remove jump
                removeJumpFrom(node, node.target)
            }
        }
    }

    override fun visitIfNE(node: Jump, condition: Node) {
        visitExpr(condition)
        node.valueInfo.expect(BooleanType)
        node.valueInfo.onValue(BooleanType) { value ->
            if (!value) { // jump so remove continuous
                removeContinuousJump(node)
            } else { // continuous so remove jump
                removeJumpFrom(node, node.target)
            }
        }
    }

    override fun visitInLoopJump(node: Jump, isContinue: Boolean) {
        // nop
    }

    override fun visitSwitch(node: Jump, value: Node, cases: List<Pair<Jump, Node>>) {
        visitExpr(value)
        for ((_, caseValue) in cases) {
            visitExpr(caseValue)
        }
    }

    override fun visitTarget(node: Node) {
        processAsTarget(node)
    }

    override fun visitLabel(node: Node): ProcessResult = statementsBlock(node)

    override fun visitBlock(node: Node): ProcessResult = statementsBlock(node)

    override fun visitLoop(node: Node): ProcessResult = statementsBlock(node)

    override fun visitInternalLocalBlock(node: Node): ProcessResult = statementsBlock(node)

    override fun visitTryBlock(node: Jump): ProcessResult {
        node.tryInfo.catchVariable?.valueInfo
            ?.exactly(InternalValueType(Throwable::class.java))
        node.tryInfo.finallyVariable?.valueInfo
            ?.exactly(InternalValueType(Throwable::class.java))
        return statementsBlock(node)
    }

    override fun visitFinally(node: Node) {
        node.realLocalVarId.valueInfo.expect(InternalValueType(Throwable::class.java))
        statementsBlock(node)
    }

    override fun visitVariableDefinition(node: Node, variables: List<Pair<Name, Node?>>, kind: Int) {
        for ((name, initializer) in variables) {
            if (initializer != null) {
                visitExpr(initializer)
                initializer.valueInfo.assignTo(name.valueInfo)
            }
        }
    }

    override fun visitThrow(node: Node, throws: Node) {
        visitExpr(throws)
    }

    override fun visitRethrow(node: Node) {
        node.realLocalVarId.valueInfo.expect(InternalValueType(Throwable::class.java))
    }

    override fun visitEmptyStatement(node: Node) {
        // nop
    }

    override fun visitExprStatement(node: Node, expr: Node, isForReturn: Boolean) {
        visitExpr(expr)
    }

    override fun visitFunctionStatement(node: Node) {
        unsupported("function statement")
    }

    private fun processAsTarget(node: Node) {
        for (nameToAssignTo in node.asSequence().castAsElementsAre<Name>()) {
            val toAssignId = nameToAssignTo.varId.asLocal()
            val varIds = nameToAssignTo.castAsElementsAre<Name>()
                .map { it.varId.asLocal() }
                .filterNot { it == toAssignId }

            onValueTypes(varIds.asSequence().map { it.valueInfo }, varIds.size) { types ->
                val uniqTypes = types.toSet()
                @Suppress("ControlFlowWithEmptyBody")
                if (uniqTypes.size == 1) {
                    toAssignId.valueInfo.exactly(uniqTypes.single())
                } else {
                    // TODO: EITHER_TYPE
                }
            }
        }
    }

    //endregion

    @Suppress("UNUSED_PARAMETER")
    private fun removeJumpFrom(node: Jump, target: Node?) {
        // TODO("Not yet implemented")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun removeContinuousJump(node: Jump) {
        // TODO("Not yet implemented")
    }
}
// */
