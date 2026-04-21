package com.localsearch.index;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class IndexPersistence
{
    public void save(Path filePath, InvertedIndex index) throws IOException
    {
        if (filePath.getParent() != null)
        {
            Files.createDirectories(filePath.getParent());
        }
        try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(filePath)))
        {
            outputStream.writeObject(index);
        }
    }

    public InvertedIndex load(Path filePath) throws IOException, ClassNotFoundException
    {
        try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(filePath)))
        {
            return (InvertedIndex) inputStream.readObject();
        }
    }

    public boolean exists(Path filePath)
    {
        return Files.exists(filePath);
    }
}
