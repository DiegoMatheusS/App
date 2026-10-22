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
2. Migrar persistência protótipo para Room, incluir backup recuperável entre dispositivos e testes instrumentados de restauração e PDFs.
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

## PR #10 — Backup e restauração local, exportação OBJ
- **Backup manual por seletor de documentos do Android:** arquivo ZIP com até 20 projetos locais, suas plantas JPEG e histórico de medições (registro global).
- **Restauração explícita:** seleciona ZIP, exibe confirmação clara de que os projetos e o histórico serão **substituídos**, verifica o conteúdo, hashes SHA-256, limites de tamanho e referências a fotografias antes de substituir dados.
- Imagens restauradas recebem nomes privados novos, evitando sobreposição com fotos existentes. Um erro na validação deixa os projetos atuais inalterados; caso a gravação falhe, a rotina tenta reverter preferências e elimina novas imagens copiadas.
- O ZIP não é criptografado; contém plantas possivelmente sensíveis. Guarde-o em local confiável. **Não existe backup automático ou sincronização em nuvem.**
- **Exportar maquete 3D (.obj)**: modelo aproximado das paredes, portas e janelas marcadas manualmente, em metros, sem texturas nem BIM; pode ser aberto em aplicativos compatíveis com Wavefront OBJ.
- Controles usam Android Storage Access Framework e não exigem acesso irrestrito a arquivos do aparelho.
- Testes unitários de limites de arquivo, proteção contra caminhos inseguros e serialização da geometria OBJ. Testes físicos de restauração, conteúdo ZIP e abertura em visualizador 3D ainda são necessários.

## PR #11 — Relatórios PDF e validação adicional de backups
- **Exportar relatório PDF das medições** pela tela Histórico, com resultados registrados, datas e aviso de estimativa. Exportação local por seletor de documentos do Android.
- **Exportar relatório PDF do projeto atual** pelo Modo Arquiteto, com nomes de pavimentos, contagens de paredes/aberturas, extensão de paredes calibradas, pé-direito, deslocamentos e rotação.
- Relatórios A4 com paginação automática, números de página e aviso explícito de que **não são laudos certificados**. O aplicativo não envia os dados à nuvem.
- **Extração de backup ZIP reforçada** e reutilizável, testada com arquivos ZIP reais em memória, incluindo fluxo normal, tentativa de leitura fora do diretório, arquivo não permitido, manifesto ausente e manifesto compactado acima do limite.
- Na restauração, o tamanho da planta declarado pelo projeto precisa corresponder às dimensões reais da imagem JPEG. Backups inválidos são rejeitados antes de substituir os projetos.
- **Limites da validação:** testes JVM verificam a leitura de ZIP e o conteúdo/paginação do relatório, mas ainda faltam testes instrumentados no Android de backup/restauração completos, arquivos PDF abertos em leitores e comportamento em caso de falha de armazenamento.

## Scanner 3D — PR #12 (captura guiada, sem reconstrução)
- Até 8 sessões locais com nome, 20 posições de fotografia por sessão: 12 ao redor do objeto, com a câmera aproximadamente horizontal, e 8 posições elevadas.
- Em cada posição é possível **capturar pela câmera ou importar da galeria**, substituir uma fotografia ou removê-la; o aplicativo marca etapas completas, progresso e próxima posição pendente.
- Cada fotografia é reduzida para resolução máxima aproximada de 2048 px e armazenada como JPEG no diretório privado do app. As sessões são persistidas mesmo depois de fechar o aplicativo e podem ser renomeadas ou excluídas com confirmação.
- O scanner está **na fase de coleta de imagens**. Não existe reconstrução 3D, malha, nuvem de pontos, cálculo de profundidade ou medição tridimensional nessa interface.
- **Backup separado do Scanner:** é possível exportar e restaurar **cada sessão em um ZIP próprio**. O arquivo geral do Modo Arquiteto continua independente e não inclui as imagens do Scanner. Guarde os ZIPs das sessões antes de desinstalar o aplicativo.
- Câmera e galeria usam recursos nativos do Android, sem login, IA paga ou envio dos dados.
- Os testes JVM cobrem a sequência de 20 pontos, progressão e detecção de etapas inválidas. Ainda são necessários testes reais de permissão de câmera, retomada e rotação da tela.

## Scanner 3D — recuperação de sessões em ZIP
- **Exportar sessão ZIP** armazena o nome da sessão e suas imagens numeradas, com tamanho e SHA-256 para cada fotografia. Sem upload ou conversão 3D.
- **Importar sessão ZIP** restaura uma nova sessão separada, sem apagar ou alterar as existentes. Rejeita nomes de arquivos não permitidos, caminhos perigosos, dados duplicados, fotos ausentes, hashes divergentes, imagens inválidas e arquivos ZIP acima dos limites.
- Limites: até 20 fotos, 8 MB por foto e **100 MB descompactados por ZIP**; sessões acima desse tamanho precisam reduzir as imagens antes de exportar.
- O formato ZIP é **não criptografado** e pode conter imagens de locais ou objetos privados. Armazene as cópias de forma segura.
- Testes unitários exercitam exportação, importação, integridade das fotos, pacote vazio, conteúdo malicioso, manifesto ausente e adulteração de imagens.
- Testes instrumentados de restauração no Android continuam necessários para certificar a preservação de arquivos em falhas de armazenamento.

## Scanner 3D — pré-análise de imagens offline (PR #14)
- Botão **Analisar qualidade das fotos (offline)**: avalia as fotos da sessão atual sem rede nem APIs externas. Trabalha sequencialmente com miniaturas 96×96 para limitar RAM e processamento.
- Gera avisos individuais por foto sobre **exposição muito baixa/alta** e **poucos detalhes visuais** (energia média de gradientes como aproximação do contraste/foco).
- Sinaliza **pares possivelmente muito semelhantes** usando hash perceptual simples (dHash). A indicação é apenas uma heurística: fotos de lados diferentes de objetos homogêneos podem parecer iguais, e fotos repetidas com iluminação diferente podem não ser detectadas.
- Mostra a contagem de posições ainda não fotografadas. O relatório é atualizado sob demanda; trocar, excluir ou salvar fotos invalida uma análise anterior.
- **Atenção:** isto NÃO extrai correspondências entre imagens, não estima movimento da câmera, profundidade nem calcula nuvem de pontos/malha 3D. A aprovação de uma foto nessa pré-análise também não garante que a reconstrução será possível.
- Testes JVM verificam detecção de imagens escuras/claras, falta de contraste, energia de contornos, identificação aproximada de imagens parecidas, validação de entrada e posições ausentes.
- Ainda são necessários testes de processamento no aparelho físico, incluindo consumo de memória com as 20 imagens e fotos difíceis (espelhos, superfícies transparentes e fundos repetitivos).

## Scanner 3D — correspondência visual offline (PR #15)
- **Comparar pontos visuais entre fotos** examina pares sugeridos de imagens vizinhas da captura guiada, inclusive pares entre voltas horizontal e elevada. A comparação é executada **sob demanda**, localmente e sem API externa.
- O pré-processamento lê uma foto por vez, reduz para 192×192 e converte para tons de cinza. O detector seleciona até **120 pontos de alto contraste por foto**; descritores binários fixos permitem comparar pontos entre imagens com Hamming, vizinho recíproco e teste de razão.
- Um consenso geométrico simplificado por RANSAC de **transformação de similaridade 2D** avalia se os pontos encontrados são aproximadamente coerentes, gerando contagem de correspondências provisórias e consistentes. Pares inconclusivos não são automaticamente considerados fotos ruins.
- A lista mostra até 28 pares recomendados, quantidade de correspondências por par, imagens faltantes e fotos que não puderam ser lidas. Alterar a sessão/fotografia limpa o resultado anterior.
- **Limites fundamentais:** este algoritmo não estima matriz fundamental, pose 3D, posição da câmera, pontos 3D, profundidade nem gera malha. Uma transformação 2D pode indicar coerência visual em parte da imagem mas não demonstra geometria do objeto. Cenas repetitivas, rotações de objeto, oclusões e grandes variações de perspectiva podem causar falsos positivos e negativos.
- Testes JVM incluem fotos sintéticas texturizadas com deslocamento conhecido, imagens sem detalhes, entrada inválida e a sequência de pares recomendados. Desempenho, consumo de RAM e resultados com objetos reais ainda precisam de testes físicos.

## Importação de plantas, contornos e nível de tanques (PR #17)
- **Modo Arquiteto:** importa foto ou página específica de PDF diretamente no Android; a página escolhida vira uma imagem de até 2048 px para calibração e marcação manual das paredes. PDF não é interpretado como CAD; texto e escala nominal não são convertidos automaticamente em paredes.
- **DXF ASCII:** importa um arquivo DXF contendo entidades `LINE` e desenha as linhas como imagem de referência, limitada a 1500 segmentos/4 MB. Esse primeiro leitor não interpreta entidades `LWPOLYLINE`, blocos, unidades, layers ou alturas 3D; é necessário definir a escala e marcar quais linhas são paredes. Arquivos DWG, RVT e IFC ainda não são suportados.
- A maquete 3D disponível usa as paredes marcadas sobre a referência e o pé-direito informado. Não é conversão automática de planta PDF/CAD em BIM.
- **Foto com contorno experimental:** após capturar/importar uma imagem e marcar uma referência física no mesmo plano, selecione Contorno por toque e toque no objeto. Uma segmentação por semelhança de cor destaca a região e estima largura/altura pela caixa delimitadora. Três tolerâncias ajustáveis; regiões muito pequenas ou que ocupam a maior parte da foto são descartadas.
- Contornos **não são medição ao vivo**, não reconhecem semanticamente objetos e não inferem distância/depth. Primeiro é preciso congelar a foto, com objeto e referência na mesma profundidade. Contornos com fundo de cor semelhante podem falhar; não confiar em resultados sem conferir os limites.
- **Tanques:** o cálculo existente de volume ocupado pelo nível foi ampliado para entrada de altura em metros, centímetros ou porcentagem de altura interna. Mostra litros/m³ ocupados e livres e, opcionalmente, massa aproximada usando densidade aparente informada pelo usuário em kg/m³. Percentual de altura não equivale a percentual de volume em tanque horizontal/esférico.
- Necessária validação física no Android de seletor de PDF/DXF, contorno da foto, performance e medições; os testes unitários cobrem parser DXF, seleção de cor e conversões de nível.

## PR #16 — Verificação de geometria epipolar offline
- Após as correspondências 2D, estima-se experimentalmente uma **matriz fundamental** com o método de oito pontos normalizados e RANSAC determinístico.
- Uma estimativa de erro de Sampson em pixels avalia se os pontos de duas fotografias respeitam uma restrição epipolar aproximada, resistente a algumas correspondências incorretas.
- A interface mostra **correspondências compatíveis com geometria epipolar** separadamente da contagem original de correspondências pela transformação 2D, evitando confundir os dois modelos.
- Limites explícitos de pontos e iterações; processo offline, sem API externa. Testes unitários incluem câmeras sintéticas com profundidades variadas, correspondências falsas, dados insuficientes e pontos degenerados.
- **Não** é calibração de câmera, estimação de matriz essencial, orientação/translação das câmeras, triangulação ou geração de malha. Não corrige degenerescências de cenas planas/rotação pura e não oferece precisão métrica. A qualidade exige validação física com fotos reais.

## Conversão assistida de desenho DXF para paredes e maquete 3D (PR #18)
- Arquivos DXF **ASCII** com entidades `LINE` e `LWPOLYLINE` de trechos retos são interpretados offline. Polilinhas fechadas criam as arestas finais; polilinhas com arcos (bulges) são rejeitadas, e não discretizadas silenciosamente.
- O aplicativo identifica as **camadas (layers)** do desenho e mostra quantas linhas cada uma possui. O usuário escolhe **uma camada** com as paredes pretendidas ou mantém apenas o desenho como imagem de referência.
- Para transformar a camada em paredes editáveis, é obrigatório selecionar **a unidade física real** (milímetros, centímetros, metros, polegadas ou pés). A escala é calculada da geometria DXF e preenchida na planta; não se pressupõe que todo DXF está em metros.
- Cada linha dessa camada, com comprimento físico entre 2 cm e 1 km, se torna uma parede no Modo Arquiteto, sujeita a revisão visual e ao pé-direito informado. O limite é de **300 paredes por importação** para conter problemas de desempenho.
- Antes de substituir imagem, marcações e aberturas do pavimento, a tela exibe confirmação com aviso explícito. Não há classificação automática semântica: uma camada com cotas, móveis ou instalações marcada como paredes criará paredes erradas.
- A mesma transformação XY é utilizada para rasterizar a referência e definir vértices normalizados, mantendo as medidas das paredes consistentes com a escala importada. A prévia 3D existente é utilizada sem novo serviço.
- Não é importação nativa de DWG/RVT/IFC nem BIM; blocos, curvas, cotas, aberturas automáticas e alturas contidas no CAD continuam fora do escopo. Ainda é necessário verificar unidade, camada e geometria, além de validar no Android real.
- Testes unitários cobrem a leitura de LINE/LWPOLYLINE, camadas, rejeição de arcos, conversão de unidades e consistência de medidas em metros.
