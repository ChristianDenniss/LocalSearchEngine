package com.localsearch.cli;

import java.nio.file.Paths;

public class CliParser
{
    public CliOptions parse(String[] args)
    {
        CliOptions options = new CliOptions();
        if (args.length == 0)
        {
            return options;
        }

        int i = 0;
        if ("search".equalsIgnoreCase(args[0]))
        {
            i = 1;
        }

        while (i < args.length)
        {
            String arg = args[i];
            switch (arg)
            {
                case "--limit" ->
                {
                    i++;
                    ensureValue(args, i, "--limit");
                    options.setLimit(Integer.parseInt(args[i]));
                }
                case "--explain" -> options.setExplain(true);
                case "--reindex" -> options.setReindex(true);
                case "--no-semantic" -> options.setSemantic(false);
                case "--no-graph" -> options.setGraphExpansion(false);
                case "--semantic-weight" ->
                {
                    i++;
                    ensureValue(args, i, "--semantic-weight");
                    options.setSemanticWeight(Double.parseDouble(args[i]));
                }
                case "--root" ->
                {
                    i++;
                    ensureValue(args, i, "--root");
                    options.setRootDirectory(Paths.get(args[i]));
                }
                case "--index" ->
                {
                    i++;
                    ensureValue(args, i, "--index");
                    options.setIndexFile(Paths.get(args[i]));
                }
                case "list" -> options.setListIndexed(true);
                default ->
                {
                    if (options.getQuery() == null)
                    {
                        options.setQuery(arg);
                    }
                    else
                    {
                        options.setQuery(options.getQuery() + " " + arg);
                    }
                }
            }
            i++;
        }
        return options;
    }

    private void ensureValue(String[] args, int index, String optionName)
    {
        if (index >= args.length)
        {
            throw new IllegalArgumentException("Missing value for " + optionName);
        }
    }
}
