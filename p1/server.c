/*
 * Definición del Servidor
 * Materia: Aplicaciones Para Comunicaciones en Red
 * Autos: Erreguin Franco Yair Alejandro
 */


#include <arpa/inet.h>
#include <dirent.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include <signal.h>
#include "comandos.h"

int sd_control_global;

void manejador(int sig)
{
    close(sd_control_global);
    printf("\n\n\t.:Servidor cerrado:.\n\n");
    exit(0);
}

void checlCLI(int argc)
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

int crear_canal_datos()
{
    int sd = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in addr = {.sin_family = AF_INET, .sin_addr.s_addr = INADDR_ANY, .sin_port = htons(0)};
    bind(sd, (struct sockaddr *)&addr, sizeof(addr));
    listen(sd, 1);
    return sd;
}

int obtener_puerto(int sd)
{
    struct sockaddr_in addr;
    socklen_t len = sizeof(addr);
    getsockname(sd, (struct sockaddr *)&addr, &len);
    return ntohs(addr.sin_port);
}

void enviar_listado(int data_conn)
{
    DIR *d = opendir(".");
    struct dirent *ent;
    struct stat st;
    char linea[1024], fecha[20];
    if(d)
    {
        while ((ent = readdir(d)) != NULL)
        {
            if (stat(ent->d_name, &st) == 0)
            {
                strftime(fecha, 20, "%d/%m/%y %H:%M", localtime(&st.st_mtime));
                int len = snprintf(linea, sizeof(linea), "%s %10ld %s %s\n", S_ISDIR(st.st_mode) ? "DIR " : "FILE", (long)st.st_size, fecha, ent->d_name);
                write(data_conn, linea, len);
            }
        }
        closedir(d);
    }
}

void openCLI(int sd_control)
{
    int cd_control;
    while((cd_control = accept(sd_control, NULL, NULL)) > 0)
    {
        Comando cmd;
        while (read(cd_control, &cmd, sizeof(Comando)) > 0)
        {
            if (cmd.op == OP_EXIT) break;

            //Bloque de Comandos de Control (Sin canal de datos extra)
            if (cmd.op == OP_CD || cmd.op == OP_MKDIR || cmd.op == OP_RM || cmd.op == OP_PWD || cmd.op == OP_REN)
            {
                char res_msg[512] = "OK";
                if (cmd.op == OP_CD && chdir(cmd.arg) != 0) strcpy(res_msg, "ERR: No existe el directorio");
                else if (cmd.op == OP_MKDIR && mkdir(cmd.arg, 0777) != 0) strcpy(res_msg, "ERR: Error al crear");
                else if (cmd.op == OP_RM && remove(cmd.arg) != 0) strcpy(res_msg, "ERR: Error al borrar");
                else if (cmd.op == OP_PWD) getcwd(res_msg, sizeof(res_msg));
                else if (cmd.op == OP_REN)
                {
                    char nuevo[256];
                    read(cd_control, nuevo, 256);
                    if(rename(cmd.arg, nuevo) != 0) strcpy(res_msg, "ERR: Error al renombrar");
                }
                write(cd_control, res_msg, sizeof(res_msg));
                continue;
            }

            //Bloque de Comandos de Datos (LS, GET, PUT)
            int sd_datos = crear_canal_datos();
            int puerto = obtener_puerto(sd_datos);
            write(cd_control, &puerto, sizeof(int)); // Avisar puerto al cliente
            int cd_datos = accept(sd_datos, NULL, NULL);

            if(cmd.op == OP_LS) enviar_listado(cd_datos);
            else if(cmd.op == OP_GET)
            {
                FILE *f = fopen(cmd.arg, "rb");
                if(f)
                {
                    char buf[BUFFER_SIZE];
                    size_t n;
                    while ((n = fread(buf, 1, BUFFER_SIZE, f)) > 0)
                        write(cd_datos, buf, n);
                    fclose(f);
                }
            }
            else if(cmd.op == OP_PUT)
            {
                FILE *f = fopen(cmd.arg, "wb");
                char buf[BUFFER_SIZE];
                ssize_t n;
                while ((n = read(cd_datos, buf, BUFFER_SIZE)) > 0)
                    fwrite(buf, 1, n, f);
                if(f) fclose(f);
            }
            close(cd_datos);
            close(sd_datos);
        }
        close(cd_control);
    }
}

int main(int argc, char **argv)
{
    signal(SIGINT, manejador);
    checlCLI(argc);
    char *port = *(argv+1);
    checkPORT(atoi(*(argv+1)));

    int sd_control, opt = 1, status;
    struct addrinfo hints, *servinfo;
    memset(&hints, 0, sizeof(hints));

    hints.ai_family = AF_INET;       //ipv4
    hints.ai_socktype = SOCK_STREAM; //tcp
    hints.ai_flags = AI_PASSIVE;     //ip local

    //Obtenemos info para BIND
    if((status = getaddrinfo(NULL, port, &hints, &servinfo)) != 0)
    {
        fprintf(stderr, "getaddrinfo error: %s\n", gai_strerror(status));
        return 1;
    }

    //socket()
    sd_control = socket(servinfo->ai_family, servinfo->ai_socktype, servinfo->ai_protocol);
    setsockopt(sd_control, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    //bind()
    if(bind(sd_control, servinfo->ai_addr, servinfo->ai_addrlen) < 0)
    {
        fprintf(stderr, "\n\tError en BIND\n");
        return 2;
    }

    //listen()
    listen(sd_control, 5);
    freeaddrinfo(servinfo); //Se libera la lista de direcciones porque ya se hizo bind()
    printf("\n\t.:SERVIDOR listo en puerto [%s]:.\n\n", port);

    //CLI -> read()/write()
    openCLI(sd_control);

    //close()
    close(sd_control);
    return 0;
}
