package com.localsearch.semantic;

public final class VectorMath
{
    private VectorMath()
    {
    }

    public static double cosineSimilarity(float[] left, float[] right)
    {
        if (left == null || right == null || left.length == 0 || left.length != right.length)
        {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < left.length; i++)
        {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d)
        {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public static void normalizeInPlace(float[] vector)
    {
        if (vector == null || vector.length == 0)
        {
            return;
        }
        double norm = 0.0d;
        for (float value : vector)
        {
            norm += value * value;
        }
        if (norm == 0.0d)
        {
            return;
        }
        float scale = (float) (1.0d / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++)
        {
            vector[i] *= scale;
        }
    }
}
