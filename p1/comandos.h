#ifndef COMANDOS_H
#define COMANDOS_H

#define BUFFER_SIZE 8192

//Comandos definidos
#define OP_LS    1
#define OP_CD    2
#define OP_GET   3
#define OP_PUT   4
#define OP_PWD   5
#define OP_MKDIR 6
#define OP_RM    7
#define OP_REN   8
#define OP_EXIT  9
//Faltan los de touch y rmdir


//Estructura para instanciar los comandos :p
typedef struct
{
    int op;                                  //Tipo de Operación
    char arg[256];                           //Argumentos del comando
} __attribute__((packed)) Comando;           //Evitamos el padding del compilador

#endif
