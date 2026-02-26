const s = { width: 28, height: 28, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.5, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

export const ElectronicsIcon = () => (<svg {...s}><rect x="2" y="3" width="20" height="14" rx="2" /><path d="M8 21h8m-4-4v4" /></svg>);
export const FashionIcon = () => (<svg {...s}><path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z" /></svg>);
export const HomeIcon = () => (<svg {...s}><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" /></svg>);
export const BeautyIcon = () => (<svg {...s}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>);
export const SportsIcon = () => (<svg {...s}><circle cx="12" cy="12" r="10" /><path d="M4.93 4.93l14.14 14.14M19.07 4.93 4.93 19.07" /></svg>);
export const ToysIcon = () => (<svg {...s}><path d="M12 2a5 5 0 0 1 5 5v1h1a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2h1V7a5 5 0 0 1 5-5z" /></svg>);
export const BooksIcon = () => (<svg {...s}><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /></svg>);
export const FoodIcon = () => (<svg {...s}><path d="M18 8h1a4 4 0 0 1 0 8h-1" /><path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" /><line x1="6" y1="1" x2="6" y2="4" /><line x1="10" y1="1" x2="10" y2="4" /><line x1="14" y1="1" x2="14" y2="4" /></svg>);
export const AutomotiveIcon = () => (<svg {...s}><path d="M5 17H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v9a2 2 0 0 1-2 2h-3" /><circle cx="7.5" cy="17.5" r="2.5" /><circle cx="17.5" cy="17.5" r="2.5" /></svg>);
export const HealthIcon = () => (<svg {...s}><path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" /></svg>);
export const DefaultCategoryIcon = () => (<svg {...s}><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /></svg>);

export type CategoryMeta = { accent: string; icon: React.ReactNode };

const CATEGORY_META: Record<string, CategoryMeta> = {
  electronics: { accent: "#00d4ff", icon: <ElectronicsIcon /> },
  fashion: { accent: "#f472b6", icon: <FashionIcon /> },
  home: { accent: "#f59e0b", icon: <HomeIcon /> },
  beauty: { accent: "#d946ef", icon: <BeautyIcon /> },
  sports: { accent: "#10b981", icon: <SportsIcon /> },
  toys: { accent: "#fbbf24", icon: <ToysIcon /> },
  books: { accent: "#818cf8", icon: <BooksIcon /> },
  food: { accent: "#ef4444", icon: <FoodIcon /> },
  automotive: { accent: "#94a3b8", icon: <AutomotiveIcon /> },
  health: { accent: "#2dd4bf", icon: <HealthIcon /> },
};

const DEFAULT_META: CategoryMeta = { accent: "#7c3aed", icon: <DefaultCategoryIcon /> };

export function getCategoryMeta(name: string): CategoryMeta {
  const lower = name.toLowerCase();
  for (const [key, meta] of Object.entries(CATEGORY_META)) {
    if (lower.includes(key)) return meta;
  }
  return DEFAULT_META;
}
