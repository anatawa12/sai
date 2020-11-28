# Sai

A fork of Rhino for fixRTM.

## Differences between Sai and Rhino

- Make similar to Nashorn, a ECMAScript runtime by OpenJDK
  - [x] Nashorn-like type resolving and method overload resolving
  - [x] Access to java primitive wrapper's methods from primitive values
- Features for fixRTM
  - [ ] [Custom line numbers and file names in the stacktrace](#custom-line-numbers-and-file-names-in-the-stacktrace)
  - [ ] [Compile to native class file and implement interface from a script object](#compile-to-native-class-file-and-implement-interface-from-a-script-object)

## Features of this project

### Custom line numbers and file names in the stacktrace

This feature is to support `//include` comment directive by RTM.

This feature adds ``//#sai-directive#line <line> <source-name>`` preprocessor-like directive.

this directive works like shown below:

```js main.js
// here's main.js#1
//#sai-directive#line 0 other-script-1.js
// here's other-script-1.js#1
//#sai-directive#line 1
// here's main.js#2
```

### Compile to native class file and implement interface from a script object

This feature is to compile to class file with statical typing with type inference and get faster.

#### Limitations

- if there's ``eval`` call in the functions can be called, 
  it's not possible to know which type can be assigned to a variable so this feature cannot be supported.
