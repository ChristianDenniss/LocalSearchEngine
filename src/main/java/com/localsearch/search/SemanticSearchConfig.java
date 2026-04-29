package com.localsearch.search;

public class SemanticSearchConfig
{
    public static final SemanticSearchConfig DISABLED = new SemanticSearchConfig(false, 0.70d, 0.30d, 0.15d);

    private final boolean enabled;
    private final double lexicalWeight;
    private final double semanticWeight;
    private final double semanticThreshold;

    public SemanticSearchConfig(boolean enabled, double lexicalWeight, double semanticWeight, double semanticThreshold)
    {
        this.enabled = enabled;
        this.lexicalWeight = lexicalWeight;
        this.semanticWeight = semanticWeight;
        this.semanticThreshold = semanticThreshold;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public double getLexicalWeight()
    {
        return lexicalWeight;
    }

    public double getSemanticWeight()
    {
        return semanticWeight;
    }

    public double getSemanticThreshold()
    {
        return semanticThreshold;
    }
}
