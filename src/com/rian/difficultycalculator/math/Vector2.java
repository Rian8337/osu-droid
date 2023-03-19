package com.rian.difficultycalculator.math;

import android.graphics.PointF;

/**
 * Represents a two-dimensional vector.
 */
public class Vector2 {
    /**
     * The X position of the vector.
     */
    public float x;

    /**
     * The Y position of the vector.
     */
    public float y;

    public Vector2(float value) {
        this(value, value);
    }

    public Vector2(PointF pointF) {
        this(pointF.x, pointF.y);
    }

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Multiplies this vector with another vector.
     *
     * @param vec The other vector.
     * @return The multiplied vector.
     */
    public Vector2 multiply(Vector2 vec) {
        return new Vector2(x * vec.x, y * vec.y);
    }

    /**
     * Divides this vector with a scalar.
     *
     * Attempting to divide by 0 will throw an error.
     *
     * @param divideFactor The factor to divide the vector by.
     * @return The divided vector.
     */
    public Vector2 divide(float divideFactor) {
        if (divideFactor == 0) {
            throw new ArithmeticException("Division by 0");
        }

        return new Vector2(x / divideFactor, y / divideFactor);
    }

    /**
     * Adds this vector with another vector.
     *
     * @param vec The other vector.
     * @return The added vector.
     */
    public Vector2 add(Vector2 vec) {
        return new Vector2(x + vec.x, y + vec.y);
    }

    /**
     * Subtracts this vector with another vector.
     *
     * @param vec The other vector.
     * @return The subtracted vector.
     */
    public Vector2 subtract(Vector2 vec) {
        return new Vector2(x - vec.x, y - vec.y);
    }

    /**
     * The length of this vector.
     */
    public float getLength() {
        return (float) Math.hypot(x, y);
    }

    /**
     * Performs a dot multiplication with another vector.
     *
     * @param vec The other vector.
     * @return The dot product of both vectors.
     */
    public float dot(Vector2 vec) {
        return x * vec.x + y * vec.y;
    }

    /**
     * Scales this vector.
     *
     * @param scaleFactor The factor to scale the vector by.
     * @return The scaled vector.
     */
    public Vector2 scale(float scaleFactor) {
        return new Vector2(x * scaleFactor, y * scaleFactor);
    }

    /**
     * Gets the distance between this vector and another vector.
     *
     * @param vec The other vector.
     * @return The distance between this vector and the other vector.
     */
    public float getDistance(Vector2 vec) {
        return (float) Math.hypot(x - vec.x, y - vec.y);
    }
    
    /**
     * Normalizes the vector.
     */
    public void normalize() {
        final float length = getLength();

        x /= length;
        y /= length;
    }

    /**
     * Checks whether this vector is equal to another vector.
     *
     * @param other The other vector.
     * @return Whether this vector is equal to the other vector.
     */
    public boolean equals(Vector2 other) {
        return x == other.x && y == other.y;
    }
}
