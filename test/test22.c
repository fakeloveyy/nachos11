#include "syscall.h"
#include "stdio.h"

/*
 int creat(char *name);
 int open(char *name);
 int read(int fileDescriptor, void *buffer, int count);
 int write(int fileDescriptor, void *buffer, int count);
 int close(int fileDescriptor);
 int unlink(char *name);
*/

// test creat() and open() function
void test1()
{
    printf("Test creat() and open() function\n");

    printf("Create a new file \"leishenweiwu.haha\"\n");
    int a = creat("leishenweiwu.haha");
    printf("File descriptor: %d\n", a);

    printf("Open a new file \"yishenweiwu.haha\"\n");
    int b = creat("yishenweiwu.haha");
    printf("File descriptor: %d\n", b);

    printf("Create an existing file \"leishenweiwu.haha\"\n");
    int c = creat("leishenweiwu.haha");
    printf("File descriptor: %d\n", c);

    printf("Open an existing file \"leishenweiwu.haha\"\n");
    int d = creat("leishenweiwu.haha");
    printf("File descriptor: %d\n", d);

    printf("Open the same file for a lot of times\n");
    int i;
    for (i = 0; i < 20; ++i) {
        printf("Open \"leishenweiwu.haha\" %d\n", i);
        int e = open("leishenweiwu.haha");
        printf("File descriptor: %d\n", e);
    }

    printf("Test finished\n");
}

// test close()
void test2()
{
    printf("Test exec() and join() function\n");
    printf("Create a new process\n");
    char *argument[] = {"childprocess.coff", "hello", "world"};
    int a = exec("childprocess.coff", 3, argument);
    printf("Start executing the child process with pid %d\n", a);
    int c = 0;
    int b = join(a, &c);
    printf("Join the child process\n");
    exit(0);
}

void test3()
{
    
}

int main()
{
    test2();
}

