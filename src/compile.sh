#!/bin/bash

javac Server.java

if [ $? -eq 0 ]; then
    echo "Compilation successful. You can now run your server."
else
    echo "Compilation failed. Please check the error messages above."
fi
