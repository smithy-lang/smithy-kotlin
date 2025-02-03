#ifndef ENVIRON_H
#define ENVIRON_H

// External declaration to get environment variables
extern char **environ;

// Helper function to get the environ pointer
char** get_environ_ptr() {
    return environ;
}

#endif
