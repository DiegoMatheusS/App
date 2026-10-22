# Medidor — Android nativo

Primeira entrega do app de medições, em **Kotlin + Jetpack Compose**. Android 11+ (API 30). Versão de desenvolvimento, não publicada na Play Store.

## O que já existe

- Navegação e identidade visual inicial para os módulos de medição, tanques, câmera, projetos e Pro.
- Cálculo offline de caixas (volume, área de piso e área superficial).
- Cálculo de tanques cilíndricos verticais/horizontais e esféricos, com nível de líquido, capacidade total e livre.
- Foto capturada via CameraX **ou importada da galeria**; calibração por moeda ou distância conhecida (mm/cm/m), quatro pontos no **mesmo plano**, com opção de desfazer marcações.
- Histórico local de resultados, exclusão individual com confirmação e compartilhamento como relatório de texto.
- **Modo Arquiteto (prévia sem IA):** importar imagem da planta, definir escala por dois pontos de referência conhecida, marcar paredes, exibir/ocultar linhas e cotas, informar pé-direito, salvar localmente, adicionar até seis pavimentos e duplicar o pavimento atual. Inclui **maquete geométrica 3D interativa**, com rotação por toque, zoom por gesto, portas/janelas dimensionadas manualmente e separação de pavimentos. **Ainda não é AR, CAD ou BIM.**
- Tela informativa Pro completo (preço de referência **R$ 129,90**, pagamento único). **Não há compra habilitada nem desbloqueio falso.**
- Testes unitários para cálculos e calibração.

## Tecnologias

Android Gradle Plugin 9.2.1, Gradle 9.4.1, Kotlin embutido do AGP, Compose (BOM 2026.09.00), CameraX 1.6.2. `compileSdk 37`, `targetSdk 36`, `minSdk 30`, Java 17.

No Android Studio: abrir a pasta do projeto, instalar Android SDK 37 e sincronizar Gradle. Na CLI, com Gradle 9.4.1 instalado: `gradle :app:assembleDebug :app:testDebugUnitTest`.

## Limites reais

- Medidas da câmera são **estimativas planas**, com pontos escolhidos manualmente e referência física no mesmo plano. Não medem profundidade ou diâmetro de tanques distantes.
- Os tanques são sólidos geométricos ideais; não contabilizam deformações, teto/fundo complexo, internos nem volume morto. Não substituem arqueação/certificação.
- A leitura **automática** de plantas, modelagem CAD/BIM, ancoragem ARCore, scanner 3D, detecção de tijolos por IA, Room e Play Billing ainda **não estão implementados**; ficam para etapas seguintes.
- Calibração por moeda usa diâmetros da **segunda família do Real**.
- Dados locais são protótipo; migração para Room antes de uso em produção.
- A primeira entrega não foi validada fisicamente em aparelho.

## Roadmap

1. Melhorar foto calibrada: retificação de perspectiva, zoom de precisão, galeria de imagens associadas às medições.
2. Persistência Room, projetos/relatórios e testes instrumentados.
3. Modo Arquiteto: retificação de perspectiva, alinhamento manual entre andares, editor de paredes e aberturas aprimorado -> ARCore.
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
