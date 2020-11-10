package com.anatawa12.sai;

public class RuntimeErrors {
    public static EvaluatorException reportRuntimeError0(String messageId)
    {
        String msg = ScriptRuntime.getMessage0(messageId);
        return Context.reportRuntimeError(msg);
    }

    public static EvaluatorException reportRuntimeError1(String messageId,
                                                  Object arg1)
    {
        String msg = ScriptRuntime.getMessage1(messageId, arg1);
        return Context.reportRuntimeError(msg);
    }

    public static EvaluatorException reportRuntimeError2(String messageId,
                                                  Object arg1, Object arg2)
    {
        String msg = ScriptRuntime.getMessage2(messageId, arg1, arg2);
        return Context.reportRuntimeError(msg);
    }

    public static EvaluatorException reportRuntimeError3(String messageId,
                                                  Object arg1, Object arg2,
                                                  Object arg3)
    {
        String msg = ScriptRuntime.getMessage3(messageId, arg1, arg2, arg3);
        return Context.reportRuntimeError(msg);
    }

    public static EvaluatorException reportRuntimeError4(String messageId,
                                                  Object arg1, Object arg2,
                                                  Object arg3, Object arg4)
    {
        String msg
                = ScriptRuntime.getMessage4(messageId, arg1, arg2, arg3, arg4);
        return Context.reportRuntimeError(msg);
    }
}
