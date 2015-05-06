#include "syscall.h"

// test creat() and open()
void test1_1()
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

// test write()
void test1_2()
{
    printf("Test write() function\n");

    char *buffer = "leishenweiwu haha\n";

    printf("Write data to stdout\n");
    int a = write(1, buffer, 18);
    printf("Return value: %d\n", a);

    int f = creat("leishenweiwu.haha");
    printf("Write data to \"leishenweiwu.haha\"\n");
    a = write(f, buffer, 18);
    printf("Return value: %d\n", a);

    printf("Test finished\n");
}

// test read()
void test1_3()
{
    printf("Test read() function\n");

    char buffer[5];

    printf("Read 3 bytes from stdin\n");
    int a = read(0, buffer, 3);
    printf("Return value: %d\n", a);
    printf("Read chars: %c %c %c\n", buffer[0], buffer[1], buffer[2]);

    int f = open("leishenweiwu.haha");
    printf("Read 3 bytes from \"leishenweiwu.haha\"\n");
    int b = read(f, buffer, 3);
    printf("Return value: %d\n", b);
    printf("Read chars: %c %c %c\n", buffer[0], buffer[1], buffer[2]);

    printf("Test finished\n");
}

// test close() and unlink()
void test1_4()
{
    printf("Test close() and unlink() function\n");

    printf("Close stdin\n");
    int a = close(0);
    printf("Return value: %d\n", a);

    printf("Close a file\n");
    int f = creat("leishenweiwu.haha");
    a = close(f);
    printf("Return value: %d\n", a);
    
    printf("Close a file which does not exist\n");
    a = close(15);
    printf("Return value: %d\n", a);

    printf("Unlink an existing file \"leishenweiwu.haha\"\n");
    a = unlink("leishenweiwu.haha");
    printf("Return value: %d\n", a);
    
    printf("Unlink an inexisting file \"qishenweiwu.haha\"\n");
    a = unlink("qishenweiwu.haha");
    printf("Return value: %d\n", a);

    printf("Unlink a file \"leishenweiwu.haha\" which is already opened\n");
    f = creat("leishenweiwu.haha");
    a = unlink("leishenweiwu.haha");
    printf("Return value: %d\n", a);

    printf("Try to open \"leishenweiwu.haha\" when it is already unlinked but not removed\n");
    a = open("leishenweiwu.haha");
    printf("Return value: %d\n", a);

    printf("Test finished\n");
}

int main()
{
    test1_4();
}

