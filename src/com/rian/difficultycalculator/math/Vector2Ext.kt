@file:JvmName("Vector2Utils")

package com.rian.difficultycalculator.math

/**
 * Multiplies this integer to a vector.
 *
 * @param vec The vector to multiply to.
 * @return A vector scaled with this integer.
 */
operator fun Int.times(vec: Vector2) = vec * this

/**
 * Multiplies this float to a vector.
 *
 * @param vec The vector to multiply to.
 * @return A vector scaled with this float.
 */
operator fun Float.times(vec: Vector2) = vec * this

/**
 * Multiplies this double to a vector.
 *
 * @param vec The vector to multiply to.
 * @return A vector scaled with this double.
 */
operator fun Double.times(vec: Vector2) = vec * this
