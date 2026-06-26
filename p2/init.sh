#!/usr/bin/zsh

kitty zsh -c "java -cp ".:jl-1.0.1.jar" Servidor" &
kitty zsh -c "java -cp ".:jl-1.0.1.jar" Cliente" &
