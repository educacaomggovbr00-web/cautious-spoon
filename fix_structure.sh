#!/bin/bash
# Script para organizar o Monstro V18
mkdir -p app/src/main/java/com/ncorti/kotlin/template/app/
mv app/src/main/java/*.kt app/src/main/java/com/ncorti/kotlin/template/app/ 2>/dev/null
mv app/src/main/*.kt app/src/main/java/com/ncorti/kotlin/template/app/ 2>/dev/null
echo "Pastas organizadas com sucesso!"
