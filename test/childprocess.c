//
//  childprocess.c
//  
//
//  Created by 雷志贤 on 15/5/6.
//
//

#include "syscall.h"
#include "stdio.h"

int main(int argc, const char *argv[]) {
    int i;
    printf("A process is created\n");
    for (i = 0; i < argc; ++i) {
        printf("%s\n", argv[i]);
    }
    printf("Process finished\n");
}
