package com.anatawa12.sai.stia;

import com.anatawa12.sai.Token;

public class ExToken {
    public static final int
            CONVERT_EXCEPTION = -1,  // ScriptRuntime.newErrorForThrowable
            INC_DEC_NAME      = -2,
            __UNUSED__ = 0;

    public static String name(int token) {
        if (token >= 0) return Token.name(token);
        switch (token) {
            case CONVERT_EXCEPTION:
                return "CONVERT_EXCEPTION";
            case INC_DEC_NAME:
                return "INC_DEC_NAME";
            default:
                throw new IllegalStateException(String.valueOf(token));
        }
    }
}
