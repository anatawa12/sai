/**
 * <b>Statically Typed Interface Adapter</b>
 *
 * <p>
 * current limitations:
 * <ul>
 *     <li>
 *         Statements or expressions make not possible to resolve objects in scope are not allowed.
 *
 *         <ul>
 *             <li>{@code with(){}} statement</li>
 *             <li>calling {@code eval()} function</li>
 *             <li>Dynamical indexing to the Scope object</li>
 *             <li>calling {@code importPackage()} and {@code import} functions</li>
 *         </ul>
 *     </li>
 *     <li>
 *         Modifying or changing type of {@code this} object.
 *
 *         <ul>
 *             <li>{@code Function.prototype.apply()},
 *             {@code Function.prototype.call()}, or
 *             {@code Function.prototype.bind()}
 *             with {@code thisArg} which is not match to {@code this} type of the function.
 *             (it always be allowed for functions which is not using {@code this})</li>
 *             <li>Assigning functions using {@code this} to some objects or variables.</li>
 *         </ul>
 *     </li>
 *     <li>
 *         If most variable are not possible to infer type.<br />
 *         Those Statements often make this situation.
 *         <ul>
 *             <li>Using {@code JSObject}s.</li>
 *             <li>Using unsupported Native functions.</li>
 *             <li>Calling {@code Java.type(name)} with variables. ({@code Java.type(name)} is not implemented yet)</li>
 *         </ul>
 *     </li>
 *     <li>
 *         Replacing built-in objects.
 *     </li>
 * </ul>
 * </p>
 */
package com.anatawa12.sai.stia;
