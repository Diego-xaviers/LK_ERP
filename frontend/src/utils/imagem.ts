/**
 * Reduz uma imagem no navegador antes de enviar.
 *
 * Sem isso, uma foto de celular vira alguns MB em base64 e o servidor recusa.
 * Usado pelo perfil (foto do motorista) e pela loja (imagem do item).
 */
export function reduzirImagem(arquivo: File, ladoMaximo = 400, qualidade = 0.85): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!arquivo.type.startsWith('image/')) {
      reject(new Error('Escolha um arquivo de imagem.'));
      return;
    }
    const img = new Image();
    const url = URL.createObjectURL(arquivo);

    img.onload = () => {
      const escala = Math.min(1, ladoMaximo / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * escala);
      canvas.height = Math.round(img.height * escala);
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      resolve(canvas.toDataURL('image/jpeg', qualidade));
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('Não foi possível ler essa imagem.'));
    };
    img.src = url;
  });
}
