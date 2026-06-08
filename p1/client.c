/*
 * Definición del Cliente
 * Materia: Aplicaciones Para Comunicaciones en Red
 * Autos: Erreguin Franco Yair Alejandro
 */


#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <time.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include "comandos.h"

void checkCLI(int argc)
{
    if(argc<2)
    {
        printf("\n\tError en CLI\n\n");
        exit(EXIT_FAILURE);
    }
}

void checkPORT(int port)
{
    if(port<1024 || port>49151)
    {
        printf("\n\tError en puerto: Debes seleccion un puerto entre 1024-49151\n\n");
        exit(EXIT_FAILURE);
    }
}

void listar_local()
{
    DIR *d = opendir(".");
    struct dirent *ent;
    struct stat st;
    char fecha[20];
    if(d)
    {
        printf("\t\t[LOCAL]:\n");
        while((ent = readdir(d)) != NULL)
        {
            stat(ent->d_name, &st);
            strftime(fecha, 20, "%d/%m/%y %H:%M", localtime(&st.st_mtime));
            printf("\t\t%s %10ld %s %s\n", S_ISDIR(st.st_mode) ? "DIR " : "FILE", (long)st.st_size, fecha, ent->d_name);
        }
        closedir(d);
    }
}

void touch_file(const char *filename) {
    int fd = open(filename, O_WRONLY | O_CREAT, 0644);
    if (fd < 0) {
        perror("Error opening/creating file");
        return;
    }
    close(fd);
    if (utimensat(AT_FDCWD, filename, NULL, 0) < 0) {
        perror("Error updating timestamp");
    }
}

void prep_socket(struct addrinfo *hints, struct addrinfo **servinfo, char *port)
{
    int status;
    memset(hints, 0, sizeof(*hints));

    hints->ai_family = AF_INET;       //ipv4
    hints->ai_socktype = SOCK_STREAM; //tcp

    //Obtenemos info del socket
    if((status = getaddrinfo("127.0.0.1", port, hints, servinfo)) != 0)
    {
        fprintf(stderr, "\nNo se encontro el servidor en el puerto [%d] :(\n\n",atoi(port));
        exit(EXIT_FAILURE);
    }
}

int conectar_datos(int puerto)
{
    int sd = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in addr = {.sin_family = AF_INET, .sin_port = htons(puerto)};
    inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);
    if(connect(sd, (struct sockaddr *)&addr, sizeof(addr)) < 0) return -1;
    return sd;
}

void openCLI(int cd_control)
{
    char line[512], mode[16], act[16], arg[256];

    while(printf("\tp1_cli-> ") && fgets(line, sizeof(line), stdin))
    {
        arg[0] = '\0';
        int n = sscanf(line, "%s %s %s", mode, act, arg);

        if(n < 1) continue;
        Comando cmd = {0};
        if(strcmp(mode, "local") == 0) //Operaciones en el cliente
        {
            if(strcmp(act, "ls") == 0) listar_local();
            else if(strcmp(act, "pwd") == 0) { getcwd(line,512); printf("\t\t[LOCAL]: %s\n", line); }
            else if (strcmp(act, "cd") == 0) chdir(arg);
            else if (strcmp(act, "mkdir") == 0) mkdir(arg, 0777);
            else if (strcmp(act, "rmdir") == 0) rmdir(arg);
            else if (strcmp(act, "rm") == 0) remove(arg);
            else if (strcmp(act, "touch") == 0) touch_file(arg);
        }
        else if(strcmp(mode, "remoto") == 0)
        {
            if (strcmp(act, "ls") == 0) cmd.op = OP_LS;
            else if (strcmp(act, "cd") == 0) cmd.op = OP_CD;
            else if (strcmp(act, "get") == 0) cmd.op = OP_GET;
            else if (strcmp(act, "put") == 0) cmd.op = OP_PUT;
            else if (strcmp(act, "pwd") == 0) cmd.op = OP_PWD;
            else if (strcmp(act, "mkdir") == 0) cmd.op = OP_MKDIR;
            else if (strcmp(act, "rm") == 0) cmd.op = OP_RM;
            else if (strcmp(act, "ren") == 0) cmd.op = OP_REN;
            else if (strcmp(act, "exit") == 0)
            {
                cmd.op = OP_EXIT;
                write(cd_control, &cmd, sizeof(Comando));
                break;
            }
            else continue;

            //Armamos el argumento
            strncpy(cmd.arg, arg, 256);
            write(cd_control, &cmd, sizeof(Comando));

            //Si se renombra entonces mandamos el nuevo nombre
            if (cmd.op == OP_REN)
            {
                char nuevo[256];
                printf("Nuevo nombre: ");
                fgets(nuevo, 256, stdin);
                nuevo[strcspn(nuevo, "\n")] = 0;
                write(cd_control, nuevo, 256);
            }

            if (cmd.op != OP_LS && cmd.op != OP_GET && cmd.op != OP_PUT)
            {
                char msg[512];
                read(cd_control, msg, sizeof(msg));
                printf("[SERVER]: %s\n", msg);
                continue;
            }

            //Preparamos el enlace de datos en el puerto que nos mande el servidor
            int puerto;
            read(cd_control, &puerto, sizeof(int));
            int sd_datos = conectar_datos(puerto);
            if (sd_datos < 0) continue;

            //Buffer
            char buf[BUFFER_SIZE];
            ssize_t bn;
            if(cmd.op == OP_LS)
            {
                while ((bn = read(sd_datos, buf, BUFFER_SIZE)) > 0)
                    write(STDOUT_FILENO, buf, bn);
            }
            else if(cmd.op == OP_GET)
            {
                FILE *f = fopen(arg, "wb");
                while ((bn = read(sd_datos, buf, BUFFER_SIZE)) > 0)
                    fwrite(buf, 1, bn, f);
                fclose(f);
            }
            else if(cmd.op == OP_PUT)
            {
                FILE *f = fopen(arg, "rb");
                if(f)
                {
                    while ((bn = fread(buf, 1, BUFFER_SIZE, f)) > 0)
                        write(sd_datos, buf, bn);
                    fclose(f);
                }
            }
            close(sd_datos);
        }
    }
}

int main(int argc, char **argv)
{
    //Validación
    checkCLI(argc);
    char *port = *(argv+1);
    checkPORT(atoi(*(argv+1)));

    //Variables
    int cd_control;
    struct addrinfo hints, *servinfo;

    //Preparamos el socket
    prep_socket(&hints, &servinfo, port);

    //socket()
    cd_control = socket(servinfo->ai_family, servinfo->ai_socktype, servinfo->ai_protocol);

    //connect()         servinfo->ai_addr ya tiene la ip y el puerto en el endianess correcto :)
    if(connect(cd_control, servinfo->ai_addr, servinfo->ai_addrlen) < 0)
    {
        fprintf(stderr, "\n\tError en connect() :'(\n");
        return 2;
    }
    freeaddrinfo(servinfo); //Ya se hizo connect, ya no necesitamos la lista

    //CLI ->read()/write()
    openCLI(cd_control);

    //close()
    close(cd_control);
    return 0;
}
