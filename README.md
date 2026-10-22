# Medidor — Android nativo

Primeira entrega do app de medições, em **Kotlin + Jetpack Compose**. Android 11+ (API 30). Versão de desenvolvimento, não publicada na Play Store.

## O que já existe

- Navegação e identidade visual inicial para os módulos de medição, tanques, câmera, projetos e Pro.
- Cálculo offline de caixas (volume, área de piso e área superficial).
- Cálculo de tanques cilíndricos verticais/horizontais e esféricos, com nível de líquido, capacidade total e livre.
- Foto capturada via CameraX **ou importada da galeria**; calibração por moeda ou distância conhecida (mm/cm/m), quatro pontos no **mesmo plano**, com opção de desfazer marcações.
- Histórico local de resultados, exclusão individual com confirmação e compartilhamento como relatório de texto.
- **Modo Arquiteto (prévia sem IA):** importar imagem da planta, definir escala por dois pontos de referência conhecida, marcar paredes, exibir/ocultar linhas e cotas, informar pé-direito, salvar localmente, adicionar até seis pavimentos e duplicar o pavimento atual. Inclui **maquete geométrica 3D interativa**, com rotação por toque, zoom por gesto, portas/janelas dimensionadas manualmente e separação de pavimentos. **Não é CAD ou BIM.** Há agora um modo AR experimental em dispositivos compatíveis.
- Tela informativa Pro completo (preço de referência **R$ 129,90**, pagamento único). **Não há compra habilitada nem desbloqueio falso.**
- Testes unitários para cálculos e calibração.

## Tecnologias

Android Gradle Plugin 9.2.1, Gradle 9.4.1, Kotlin embutido do AGP, Compose (BOM 2026.09.00), CameraX 1.6.2. `compileSdk 37`, `targetSdk 36`, `minSdk 30`, Java 17.

No Android Studio: abrir a pasta do projeto, instalar Android SDK 37 e sincronizar Gradle. Na CLI, com Gradle 9.4.1 instalado: `gradle :app:assembleDebug :app:testDebugUnitTest`.

## Limites reais

- Medidas da câmera são **estimativas planas**, com pontos escolhidos manualmente e referência física no mesmo plano. Não medem profundidade ou diâmetro de tanques distantes.
- Os tanques são sólidos geométricos ideais; não contabilizam deformações, teto/fundo complexo, internos nem volume morto. Não substituem arqueação/certificação.
- A leitura **automática** de plantas, modelagem CAD/BIM, alinhamento estrutural certificado de pavimentos, scanner 3D, detecção de tijolos por IA, Room e Play Billing ainda **não estão implementados**; ficam para etapas seguintes.
- Calibração por moeda usa diâmetros da **segunda família do Real**.
- Dados locais são protótipo; migração para Room antes de uso em produção.
- A primeira entrega não foi validada fisicamente em aparelho.

## Roadmap

1. Melhorar foto calibrada: retificação de perspectiva, zoom de precisão, galeria de imagens associadas às medições.
2. Migrar persistência protótipo para Room, backups de projetos, relatórios e testes instrumentados.
3. Modo Arquiteto: melhorar escala/calibração entre plantas, edição detalhada de paredes/aberturas e validação física do ARCore.
4. Scanner 3D por fotogrametria offline, com benchmarks de hardware.
5. Detecção/contagem de tijolos com modelo de IA local opcional.
6. Pro Completo: implementar Google Play Billing, validação e restauração real da compra única.

## CI

Workflow compila debug e executa testes quando houver push ou PR (necessita Android SDK e Gradle compatíveis no runner).

## Modo Arquiteto — notas da malha 3D
- É necessário fotografar/importar planta ortogonal, calibrar a escala e marcar paredes.
- Para inserir portas/janelas: escolha a parede, digite largura, altura, centro da abertura (%) e peitoril (janela). Sobreposições e dimensões inválidas são bloqueadas.
- A prévia renderiza paredes com espessura ilustrativa de 14 cm; isso **não é uma medição real da espessura**.
- Os pavimentos são centrados individualmente, portanto o encaixe entre desenhos distintos é apenas aproximado até implementarmos alinhamento por referências.
- Há testes unitários das aberturas, áreas de superfície e conversões; ainda é necessária validação visual em aparelho real.

## AR experimental (ARCore, sem IA generativa)
- `AR na mesa`: toca uma superfície horizontal reconhecida e posiciona a maquete; os botões permitem mudar o tamanho e reposicionar. Escala **de apresentação**, não 1:1.
- `AR sobre papel`: importa a mesma planta que foi impressa, informa sua **largura física em centímetros** e tenta reconhecer a imagem com ARCore Augmented Images. Ao reconhecer, cria uma âncora na folha e miniaturiza o modelo conforme o tamanho impresso.
- A imagem deve ter detalhes visuais distintos; uma planta de linhas muito uniformes pode não ser detectável. Papel fixo e iluminado funciona melhor. Não trata documento PDF diretamente, nem detecta qualquer planta automaticamente.
- Em ambos os casos é preciso aparelho compatível com ARCore e Serviços do Google Play para RA disponíveis. O modo manual permanece utilizável nos demais aparelhos.
- Renderização local via OpenGL ES 2.0; não há login, API de IA ou upload. Sem sensores compatíveis ou câmera disponível o app deve exibir uma explicação.
- Estado **experimental**: integração depende de validação física da âncora, orientação da maquete, conforto de zoom e estabilidade do rastreamento.

## Alinhamento manual e estabilidade AR (evolução)
- Cada pavimento após o térreo aceita deslocamentos X/Y em metros (de -20 a 20), usados tanto na maquete 3D quanto no AR. O térreo fica como referência; escala/rotação/identificação de pilares ou escadas **não são automaticamente reconciliadas**.
- O projeto local salva esses deslocamentos e continua lendo projetos anteriores (valores antigos assumem 0,0).
- O AR sobre o papel agora distingue rastreamento visual direto, última posição estimada quando o papel sai da câmera, rastreamento pausado e referência perdida. Para melhores resultados, a folha deve permanecer fixa, plana, bem iluminada e ocupar pelo menos 25% do enquadramento inicialmente.
- Aviso de imagem com poucos detalhes e mensagem específica quando o ARCore rejeitar a imagem. Esse aviso é apenas heurístico; aprovação visual e precisão permanecem dependentes do ARCore e de testes físicos.
- A renderização da maquete reutiliza buffers geométricos, reduzindo alocações repetidas durante a navegação.

## PR #7 — Retificação de folhas e pontos de referência
- **Corrigir perspectiva (manual):** selecionar os quatro cantos de uma folha retangular e plana (superior esquerdo, superior direito, inferior direito, inferior esquerdo), informar **largura e altura reais da folha em centímetros**, e aplicar uma transformação projetiva local. A imagem resultante fica limitada a 2048 px por lado. Não é detecção automática da planta.
- A retificação aplica a proporção física **da própria folha**. É válida apenas se os pontos representam os cantos dessa folha plana; não corrige aberração óptica, curvatura, má impressão ou distorções internas de desenhos.
- **Proteção das marcações:** como a perspectiva altera a geometria da imagem, a correção limpa escala, paredes, portas, janelas e ponto de alinhamento do pavimento afetado, com confirmação antes da substituição. Refaça a escala após corrigir.
- **Alinhamento por um ponto arquitetônico:** tocar em um ponto correspondente no térreo e no andar superior, com escalas previamente definidas, para calcular os deslocamentos X/Y automaticamente. Ajustes manuais continuam disponíveis. Um ponto determina a translação, **não** corrige diferença de rotação ou escala entre plantas.
- Os pontos são persistidos nos projetos locais com compatibilidade com arquivos anteriores; testes cobrem o quadrilátero, proporção de saída, orientação, pontos inválidos e deslocamentos.
- A conversão continua sendo **uma estimativa** baseada na qualidade da planta e da calibração; não é aferição de obra nem medição certificada.

## PR #8 — Alinhamento de dois pontos e rotação
- Escolha dois pontos **correspondentes** no térreo e no outro pavimento usando os modos **Ponto 1** e **Ponto 2**. Cada planta precisa estar calibrada.
- **Alinhar posição e rotação (2 pontos)** calcula um ângulo e os deslocamentos X/Y, aplicados igualmente à prévia 3D e à representação AR.
- **Alinhar só posição (1 ponto)** continua funcionando, agora respeitando a rotação manual de cada pavimento.
- **Rotação manual** disponível de -180° a +180°; centralizar restaura rotação e translação a zero.
- Os pontos devem estar separados por pelo menos 0,5 m em cada planta. Diferença maior que 8% entre as distâncias dos dois pontos indica **escalas inconsistentes** e bloqueia o alinhamento automático; a escala não é ajustada.
- O formato local do projeto foi estendido mantendo os projetos anteriores sem rotação nem segundo ponto. Importar/corrigir uma planta apaga pontos de alinhamento inválidos daquela folha.
- Ainda é um alinhamento 2D estimado: não corrige deformações na planta, não confirma pilares/escadas e **não substitui verificação física de obra**.

## PR #9 — Zoom de precisão e projetos independentes

- Marcação no Modo Arquiteto com zoom de 1× a 6× e deslocamento usando **dois dedos**. Imagem e cotas recebem a mesma transformação visual, para os toques continuarem associados aos pontos da planta. Botões +, − e Centralizar.
- **Projetos locais separados:** criar, escolher, renomear, duplicar e excluir (com confirmação), até 20. Cada projeto mantém sua própria lista de andares, escalas, paredes e aberturas.
- Compatibilidade com projetos de versões anteriores: o slot original `architect_plan_v1/plan` continua representando o projeto ativo; o gerenciamento acrescenta um índice e cópias de projetos inativos. A migração não elimina a chave de dados antiga.
- Uma duplicação pode compartilhar referências às mesmas imagens privadas. A exclusão de um projeto **não apaga as imagens do armazenamento**, para evitar quebrar outro projeto que use a mesma foto. Limpeza de imagens órfãs exige migração futura mais cuidadosa.
- Os projetos não possuem sincronização, login nem backup automático. Antes de dados críticos de produção, migrar para Room e implementar exportação/importação com verificação de integridade.
- O zoom melhora a seleção manual, mas não aumenta a resolução original da foto nem torna medições geometricamente certificadas.
