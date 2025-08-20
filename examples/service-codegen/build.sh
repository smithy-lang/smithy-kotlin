#!/bin/bash

if [ -d "build" ]; then
    read -p "The 'build' directory already exists. Removing it will delete previous build artifacts. Continue? (y/n): " choice
    case "$choice" in
        y|Y )
            gradle clean
            echo "Previous build directory removed."
            ;;
        * )
            echo "Aborted."
            exit 1
            ;;
    esac
fi

gradle build
