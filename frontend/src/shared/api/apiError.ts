export interface FieldError {
  field: string;
  message: string;
}

export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'aborted';

interface ProblemBody {
  type?: unknown;
  title?: unknown;
  detail?: unknown;
  traceId?: unknown;
  errors?: unknown;
}

const asString = (v: unknown): string | undefined => (typeof v === 'string' ? v : undefined);

/** Ostatni segment URL typu Problem Details, np. "/problems/slot-taken" -> "slot-taken". */
export function problemTypeSlug(type: string | undefined): string | undefined {
  if (!type || type === 'about:blank') return undefined;
  const segment = type.split('/').filter(Boolean).pop();
  return segment === '' ? undefined : segment;
}

function parseFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return [];
  const result: FieldError[] = [];
  for (const item of value as unknown[]) {
    if (typeof item === 'object' && item !== null) {
      const { field, message } = item as Record<string, unknown>;
      if (typeof field === 'string' && typeof message === 'string') {
        result.push({ field, message });
      }
    }
  }
  return result;
}

export interface ApiErrorInit {
  kind?: ApiErrorKind;
  status?: number;
  type?: string | undefined;
  title?: string | undefined;
  detail?: string | undefined;
  errors?: FieldError[];
  requestId?: string | undefined;
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number;
  readonly type: string | undefined;
  readonly title: string | undefined;
  readonly detail: string | undefined;
  readonly errors: FieldError[];
  readonly requestId: string | undefined;

  constructor(init: ApiErrorInit) {
    super(init.title ?? init.detail ?? `Błąd API (${String(init.status ?? 0)})`);
    this.name = 'ApiError';
    this.kind = init.kind ?? 'http';
    this.status = init.status ?? 0;
    this.type = init.type;
    this.title = init.title;
    this.detail = init.detail;
    this.errors = init.errors ?? [];
    this.requestId = init.requestId;
  }

  /** Buduje błąd z odpowiedzi HTTP i (opcjonalnie) sparsowanego ciała Problem Details. */
  static fromResponse(response: Response, body: unknown): ApiError {
    const problem: ProblemBody = typeof body === 'object' && body !== null ? body : {};
    return new ApiError({
      status: response.status,
      type: problemTypeSlug(asString(problem.type)),
      title: asString(problem.title),
      detail: asString(problem.detail),
      errors: parseFieldErrors(problem.errors),
      requestId: asString(problem.traceId) ?? response.headers.get('X-Request-Id') ?? undefined,
    });
  }

  static network(requestId?: string): ApiError {
    return new ApiError({ kind: 'network', title: 'Brak połączenia z serwerem', requestId });
  }
  static timeout(requestId?: string): ApiError {
    return new ApiError({ kind: 'timeout', title: 'Przekroczono limit czasu', requestId });
  }
  static aborted(requestId?: string): ApiError {
    return new ApiError({ kind: 'aborted', title: 'Żądanie anulowane', requestId });
  }

  get isNetwork(): boolean {
    return this.kind === 'network';
  }
  get isTimeout(): boolean {
    return this.kind === 'timeout';
  }
  get isAborted(): boolean {
    return this.kind === 'aborted';
  }

  isConflict(): boolean {
    return this.status === 409;
  }
  isForbidden(): boolean {
    return this.status === 403;
  }
  isUnauthorized(): boolean {
    return this.status === 401;
  }
  isServerError(): boolean {
    return this.status >= 500;
  }

  /** Błędy pól jako mapa pole -> komunikat (do setError w React Hook Form). */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.errors.map((e) => [e.field, e.message]));
  }
}
