package com.localsearch.semantic;

import com.localsearch.util.Tokenizer;

import java.util.List;

/**
 * Lightweight local embedding implementation used as a foundation.
 * It creates deterministic vectors by hashing tokens into a fixed-size space.
 */
public class HashingEmbeddingProvider
implements EmbeddingProvider
{
    private static final int DEFAULT_DIMENSIONS = 256;

    private final Tokenizer tokenizer;
    private final int dimensions;

    public HashingEmbeddingProvider(Tokenizer tokenizer)
    {
        this(tokenizer, DEFAULT_DIMENSIONS);
    }

    public HashingEmbeddingProvider(Tokenizer tokenizer, int dimensions)
    {
        if (dimensions <= 0)
        {
            throw new IllegalArgumentException("dimensions must be > 0");
        }
        this.tokenizer = tokenizer;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text)
    {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank())
        {
            return vector;
        }

        List<String> tokens = tokenizer.tokenizeForIndexing(text);
        for (String token : tokens)
        {
            int hash = token.hashCode();
            int index = Math.floorMod(hash, dimensions);
            float direction = ((hash >>> 1) & 1) == 0 ? 1.0f : -1.0f;
            vector[index] += direction;
        }
        VectorMath.normalizeInPlace(vector);
        return vector;
    }
}
