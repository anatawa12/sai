load("testsrc/assert.js");

function topLevelFunction1() {}
const unnamedFunction1 = function() {};
const lambdaFunction1 = () => {};

assertEquals("function topLevelFunction1() {}", String(topLevelFunction1));
assertEquals("function() {}", String(unnamedFunction1));
assertEquals("() => {}", String(lambdaFunction1));

'success';
