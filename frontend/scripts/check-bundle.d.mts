export const AREAS: string[];
export const BUDGET: { initial: number; area: number };
export function evaluate(
  manifest: Record<string, { file: string; isEntry?: boolean; imports?: string[] }>,
  gzipSize: (file: string) => number,
): string[];
