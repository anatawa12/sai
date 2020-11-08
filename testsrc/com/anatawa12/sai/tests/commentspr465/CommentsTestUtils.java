package com.anatawa12.sai.tests.commentspr465;

import org.junit.Assert;
import com.anatawa12.sai.CompilerEnvirons;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.EvaluatorException;
import com.anatawa12.sai.Parser;
import com.anatawa12.sai.ast.AstRoot;
import com.anatawa12.sai.testing.TestErrorReporter;


/**
 * @author ravik
 * @created 07/08/18 2:06 PM
 */
public class CommentsTestUtils {

    public static AstRoot getRhinoASTRootNode(String scriptStr, String scriptName, String[] expectedErrors, String[] expectedWarnings) {

        TestErrorReporter ter = new TestErrorReporter(expectedErrors, expectedWarnings) {
            @Override
            public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset) {
                if (expectedErrors == null) {
                    throw new UnsupportedOperationException();
                }
                return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
            }
        };
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        env.setRecordingLocalJsDocComments(true);
        env.setRecordingComments(true);
        env.setRecoverFromErrors(true);
        env.setAllowSharpComments(true);
//        env.setIdeMode(true);
        env.setErrorReporter(ter);
        AstRoot node = new Parser(env).parse(scriptStr, scriptName, 1);
        Assert.assertTrue(ter.hasEncounteredAllErrors());
        Assert.assertTrue(ter.hasEncounteredAllWarnings());
        return node;
    }
}
