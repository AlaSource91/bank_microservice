package com.alaeldin.Auth_service.util;

public final class FileUtils
{
    private FileUtils()
    {
        throw new UnsupportedOperationException("Utility class");

    }

    public static String getFileExtension(String fileName)
    {
        if (fileName == null || fileName.isBlank())
        {
            return "";
        }

        int dotIndex = fileName.lastIndexOf('.');

        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }
}
