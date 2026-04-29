package com.localsearch.semantic;

/**
 * Produces fixed-size vector embeddings for arbitrary text.
 */
public interface EmbeddingProvider
{
    float[] embed(String text);
}
