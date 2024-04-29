@echo off
set PATH=%PATH%;%GOPATH%\bin
set protodir=..\..\protos

protoc -I=%protodir% --go_out=%protodir% %protodir%/demo.proto