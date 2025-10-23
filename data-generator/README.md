# data-generator

Generador de datos realista para la simulacion

## Cómo usarlo
___
1. Primero se debe crear el ambiente virtual (Note que el nombre es environment, conserve ese nombre)
```
python -m venv environment
```
2. Activar el ambiente virtual
```
.\environment\Scripts\Activate.ps1
```
3. Instalar las librerías necesarias
```
pip install --upgrade pip
pip install -r requirements.txt
```

En VSCode se tiene que seleccionar la ruta del interprete del ambiente virtual. Configurar los settings.json como sigue:

```
    "python.defaultInterpreterPath": "${workspaceFolder}/data-generator/environment/Scripts/python.exe",
    "python.terminal.activateEnvironment": true,
```

Si después de esto sigue sin funcionar, llamar a Developer: Reload Window para que los cambios hagan efecto



