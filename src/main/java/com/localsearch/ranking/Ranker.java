package com.localsearch.ranking;

import com.localsearch.model.DocumentRecord;

public class Ranker
{
    private static final double RECENCY_WEIGHT = 0.5d;
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    public double recencyBoost(DocumentRecord document, long nowMillis)
    {
        long ageMillis = Math.max(0L, nowMillis - document.getLastModified());
        double ageDays = (double) ageMillis / MILLIS_PER_DAY;
        return RECENCY_WEIGHT / (1.0d + ageDays);
    }
}
