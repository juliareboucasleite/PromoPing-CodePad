# Como contribuir

Obrigada por considerar contribuir para o CodePad. Seguem orientações para manter o processo simples e consistente.

Podes reportar bugs abrindo uma issue e descrevendo o problema e os passos para reproduzir; sugerir melhorias usando issues para ideias de funcionalidades ou melhorias de UX; enviar código com correções, melhorias ou novas funcionalidades via pull request (PR); ou melhorar a documentação, seja no README, em comentários no código ou em textos de ajuda.

Antes de começar, verifica se já existe uma issue ou PR sobre o mesmo assunto. Para mudanças grandes, abre primeiro uma issue para alinhar a direção antes de implementar.

O ambiente de desenvolvimento requer JDK 21+ com JAVA_HOME configurado. O projeto usa o wrapper do Maven (mvnw ou mvnw.cmd). Para rodar o projeto usa .\mvnw javafx:run. Para build e testes usa .\mvnw -DskipTests package. Para empacotar no Windows, com PowerShell, usa .\package.ps1; o resultado fica em dist\CodePad.

Para enviar alterações, faz um fork do repositório e cria um branch a partir de master (ou da branch principal). Faz as alterações em commits claros e com mensagens objetivas. Garante que o projeto continua a compilar e a rodar (mvnw package e javafx:run). Abre o PR descrevendo o que foi alterado e, se aplicável, referencia a issue relacionada. A mantenedora fará a revisão e poderá pedir ajustes.

Quanto ao estilo de código, segue o estilo existente no projeto (indentação, nomes, organização). Prefere código simples e legível e evita duplicação. Comentários em português ou inglês são aceites; consistência no mesmo ficheiro é preferível.

Ao participar, concordas em respeitar o Código de Conduta do projeto (CODE_OF_CONDUCT.md).

Se tiveres dúvidas, abre uma issue com a etiqueta apropriada ou entra em contacto através do perfil da mantenedora no GitHub.

Obrigada por contribuir.
