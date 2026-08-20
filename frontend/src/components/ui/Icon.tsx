/**
 * Ícones de traço (stroke) no padrão Lucide — desenhados inline pra não
 * depender de lib externa e manter o peso visual consistente em todo o app.
 */
const PATHS: Record<string, JSX.Element> = {
  gauge: <><path d="M12 14 15.5 9.5" /><circle cx="12" cy="14" r="1.5" /><path d="M3.5 18a9 9 0 1 1 17 0" /></>,
  fileText: <><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><path d="M14 2v6h6" /><path d="M8 13h8M8 17h5" /></>,
  layers: <><path d="m12 2 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5" /><path d="m3 17 9 5 9-5" /></>,
  fuel: <><path d="M3 21h10V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v16Z" /><path d="M3 11h10" /><path d="m16 8 2.5 2.5v7a1.5 1.5 0 0 0 3 0V12l-3-3" /></>,
  truck: <><path d="M3 16V6a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v10" /><path d="M15 9h4l2.5 3.5V16h-2" /><circle cx="7" cy="18" r="2" /><circle cx="17" cy="18" r="2" /><path d="M9 18h6" /></>,
  users: <><path d="M16 20v-1a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v1" /><circle cx="9" cy="7" r="3.2" /><path d="M22 20v-1a4 4 0 0 0-3-3.8" /><path d="M16.5 4.2a3.2 3.2 0 0 1 0 5.6" /></>,
  trophy: <><path d="M7 4h10v5a5 5 0 0 1-10 0V4Z" /><path d="M7 6H4.5A1.5 1.5 0 0 0 3 7.5C3 9.4 4.7 11 7 11" /><path d="M17 6h2.5A1.5 1.5 0 0 1 21 7.5c0 1.9-1.7 3.5-4 3.5" /><path d="M12 14v3" /><path d="M8.5 21h7l-.7-3.2a1 1 0 0 0-1-.8h-3.6a1 1 0 0 0-1 .8L8.5 21Z" /></>,
  shield: <><path d="M12 2.5 20 6v6c0 4.4-3.3 8.4-8 9.5-4.7-1.1-8-5.1-8-9.5V6l8-3.5Z" /><path d="m9 12 2 2 4-4" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></>,
  bell: <><path d="M18 8.5a6 6 0 1 0-12 0c0 5-2 6.5-2 6.5h16s-2-1.5-2-6.5Z" /><path d="M13.7 19a2 2 0 0 1-3.4 0" /></>,
  moon: <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z" />,
  trendUp: <><path d="m3 16 5.5-5.5 3.5 3.5L21 5" /><path d="M15 5h6v6" /></>,
  trendDown: <><path d="m3 8 5.5 5.5 3.5-3.5L21 19" /><path d="M15 19h6v-6" /></>,
  wallet: <><path d="M3 8a2 2 0 0 1 2-2h13a1 1 0 0 1 1 1v2" /><path d="M3 8v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a1 1 0 0 0-1-1H5a2 2 0 0 1-2-2Z" /><circle cx="17" cy="13.5" r="1" /></>,
  receipt: <><path d="M5 3.5v17l2-1.2 2 1.2 2-1.2 2 1.2 2-1.2 2 1.2v-17a1 1 0 0 0-1-1H6a1 1 0 0 0-1 1Z" /><path d="M8.5 8h7M8.5 12h7" /></>,
  route: <><circle cx="6" cy="6" r="2.5" /><circle cx="18" cy="18" r="2.5" /><path d="M8.5 6H15a3 3 0 0 1 0 6H9a3 3 0 0 0 0 6h6.5" /></>,
  alert: <><path d="M12 4.5 3 19.5h18L12 4.5Z" /><path d="M12 10v4" /><path d="M12 17h.01" /></>,
  logout: <><path d="M9 21H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3" /><path d="M16 17l5-5-5-5" /><path d="M21 12H9" /></>,
  check: <path d="m5 12.5 4.5 4.5L19 7" />,
  plus: <><path d="M12 5v14" /><path d="M5 12h14" /></>,
  wrench: <><path d="M15.5 3.5a5 5 0 0 0-6.4 6.4L3.6 15.4a2 2 0 0 0 0 2.8l2.2 2.2a2 2 0 0 0 2.8 0l5.5-5.5a5 5 0 0 0 6.4-6.4l-3 3-2.8-2.8 3-3Z" /></>,
  cone: <><path d="m12 3 6.5 16h-13L12 3Z" /><path d="M8.6 12h6.8M7 16h10" /><path d="M3.5 20h17" /></>,
  siren: <><path d="M6 18v-5a6 6 0 0 1 12 0v5" /><rect x="3.5" y="18" width="17" height="3.5" rx="1" /><path d="M12 3v2M5 6.5 6.4 8M19 6.5 17.6 8" /></>,
  alertCircle: <><circle cx="12" cy="12" r="8.5" /><path d="M12 8v4.5" /><path d="M12 16h.01" /></>,
  flag: <><path d="M5 21V4" /><path d="M5 4.5h11l-1.6 3.5L16 11.5H5" /></>,
  clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 1.8" /></>,
  arrowRight: <><path d="M4 12h15" /><path d="m13 6 6 6-6 6" /></>,
  building: <><path d="M4 21V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v16" /><path d="M15 10h4a1 1 0 0 1 1 1v10" /><path d="M2.5 21h19" /><path d="M7.5 8h4M7.5 12h4M7.5 16h4" /></>,
  megaphone: <><path d="M3 11v2a1 1 0 0 0 1 1h3l7 4.5V5.5L7 10H4a1 1 0 0 0-1 1Z" /><path d="M18 9a4 4 0 0 1 0 6" /><path d="M7 14v5.5" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M19.4 14.5a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.1-2.7l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z" /></>,
  pin: <><path d="M9 3h6l-1 6 4 3v2H6v-2l4-3-1-6Z" /><path d="M12 14v7" /></>,
  edit: <><path d="M4 20h4l10-10a2.5 2.5 0 0 0-3.5-3.5L4.5 16.5 4 20Z" /><path d="m13.5 7 3.5 3.5" /></>,
  trash: <><path d="M4 6h16" /><path d="M9 6V4.5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1V6" /><path d="M6 6v13a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V6" /><path d="M10 11v6M14 11v6" /></>,
  radio: <><circle cx="12" cy="12" r="2" /><path d="M8.5 15.5a5 5 0 0 1 0-7" /><path d="M15.5 8.5a5 5 0 0 1 0 7" /><path d="M5.7 18.3a9 9 0 0 1 0-12.6" /><path d="M18.3 5.7a9 9 0 0 1 0 12.6" /></>,


};

interface Props {
  name: keyof typeof PATHS;
  size?: number;
  className?: string;
  strokeWidth?: number;
}

export default function Icon({ name, size = 18, className, strokeWidth = 1.6 }: Props) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {PATHS[name]}
    </svg>
  );
}
