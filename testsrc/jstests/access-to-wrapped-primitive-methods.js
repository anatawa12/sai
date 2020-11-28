load("testsrc/assert.js");

const str1 = "hello sai!";

assertEquals(true, str1.regionMatches(
    /*ignoreCase*/ false,
    /*offset*/     6,
    /*other*/      "this is sai",
    /*ooffset*/    8,
    /*len*/        3
));

assertEquals(true, str1.regionMatches(
    /*ignoreCase*/ true,
    /*offset*/     6,
    /*other*/      "this is sai",
    /*ooffset*/    8,
    /*len*/        3
));

assertEquals(false, str1.regionMatches(
    /*ignoreCase*/ false,
    /*offset*/     6,
    /*other*/      "this is SAI",
    /*ooffset*/    8,
    /*len*/        3
));

assertEquals(true, str1.regionMatches(
    /*ignoreCase*/ true,
    /*offset*/     6,
    /*other*/      "this is SAI",
    /*ooffset*/    8,
    /*len*/        3
));

'success';
