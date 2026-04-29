package com.localsearch.search;

/**
 * Query-time graph expansion: boosts neighbors of strong retrieval seeds using offline edges.
 */
public class GraphRetrievalConfig
{
    public static final GraphRetrievalConfig DEFAULT = new GraphRetrievalConfig(true, 0.22d, 18);
    public static final GraphRetrievalConfig DISABLED = new GraphRetrievalConfig(false, 0.0d, 0);

    private final boolean enabled;
    /** Neighbor receives up to (seedHybridScore * neighborBoostFactor). */
    private final double neighborBoostFactor;
    private final int maxSeeds;

    public GraphRetrievalConfig(boolean enabled, double neighborBoostFactor, int maxSeeds)
    {
        this.enabled = enabled;
        this.neighborBoostFactor = neighborBoostFactor;
        this.maxSeeds = maxSeeds;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public double getNeighborBoostFactor()
    {
        return neighborBoostFactor;
    }

    public int getMaxSeeds()
    {
        return maxSeeds;
    }
}
