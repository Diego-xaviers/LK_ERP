import { useRef, useState, useEffect } from 'react';

interface Props {
  onChange: (dataUrl: string | null) => void;
}

/** Canvas de assinatura — desenha com mouse ou toque, exporta como PNG base64 */
export default function SignaturePad({ onChange }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [desenhando, setDesenhando] = useState(false);
  const [temTraço, setTemTraço] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.lineWidth = 2.2;
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#1d1d1f';
  }, []);

  function posicao(e: React.MouseEvent | React.TouchEvent) {
    const canvas = canvasRef.current!;
    const rect = canvas.getBoundingClientRect();
    const point = 'touches' in e ? e.touches[0] : e;
    return { x: point.clientX - rect.left, y: point.clientY - rect.top };
  }

  function iniciar(e: React.MouseEvent | React.TouchEvent) {
    const ctx = canvasRef.current!.getContext('2d')!;
    const { x, y } = posicao(e);
    ctx.beginPath();
    ctx.moveTo(x, y);
    setDesenhando(true);
  }

  function desenhar(e: React.MouseEvent | React.TouchEvent) {
    if (!desenhando) return;
    const ctx = canvasRef.current!.getContext('2d')!;
    const { x, y } = posicao(e);
    ctx.lineTo(x, y);
    ctx.stroke();
    if (!temTraço) setTemTraço(true);
  }

  function finalizar() {
    if (!desenhando) return;
    setDesenhando(false);
    const canvas = canvasRef.current!;
    onChange(temTraço ? canvas.toDataURL('image/png') : null);
  }

  function limpar() {
    const canvas = canvasRef.current!;
    const ctx = canvas.getContext('2d')!;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    setTemTraço(false);
    onChange(null);
  }

  return (
    <div className="sigpad">
      <canvas
        ref={canvasRef}
        width={480}
        height={140}
        className="sigpad__canvas"
        onMouseDown={iniciar}
        onMouseMove={desenhar}
        onMouseUp={finalizar}
        onMouseLeave={finalizar}
        onTouchStart={iniciar}
        onTouchMove={desenhar}
        onTouchEnd={finalizar}
      />
      <div className="sigpad__footer">
        <span className="sigpad__hint">Assine com o mouse ou o dedo</span>
        <button type="button" className="sigpad__clear" onClick={limpar}>Limpar</button>
      </div>
    </div>
  );
}
