load("testsrc/assert.js");

try {
    fun();
} catch (e) {
    assertEquals(true, e.stack.indexOf("included-file.js:3") !== -1);
    assertEquals(true, e.stack.indexOf("line-directive-test.js:4") !== -1);
}

try {
    Packages.java.util.Objects.requireNonNull(null);
} catch (e) {
    assertEquals(true, e.stack.indexOf("line-directive-test.js:11") !== -1);
}

//#sai-directive#line 0 "included-file.js"

function fun () {
    Packages.java.util.Objects.requireNonNull(null);
}

try {
    fun();
} catch (e) {
    assertEquals(true, e.stack.indexOf("included-file.js:3") !== -1);
    assertEquals(true, e.stack.indexOf("included-file.js:7") !== -1);
}

try {
    Packages.java.util.Objects.requireNonNull(null);
} catch (e) {
    assertEquals(true, e.stack.indexOf("included-file.js:14") !== -1);
}

//#sai-directive#line 100 "line-directive-test.js"

try {
    fun();
} catch (e) {
    assertEquals(true, e.stack.indexOf("included-file.js:3") !== -1);
    assertEquals(true, e.stack.indexOf("line-directive-test.js:103") !== -1);
}

try {
    Packages.java.util.Objects.requireNonNull(null);
} catch (e) {
    assertEquals(true, e.stack.indexOf("line-directive-test.js:110") !== -1);
}

"success"
