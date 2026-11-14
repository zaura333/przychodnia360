import { Link } from 'react-router';
import { PERFORMER_LABELS } from '../labels';
import type { ServiceResponse } from '@/shared/api/generated/model';
import { formatDuration, formatPrice } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';

interface ServiceCardProps {
  service: ServiceResponse;
  categoryName: string | undefined;
  /** Adres szczegółów usługi (pusty: karta bez odnośnika). */
  detailsTo?: string;
  /** Adres rezerwacji (pusty: bez przycisku "Umów", np. dla ról pracowniczych). */
  bookTo?: string;
}

export function ServiceCard({ service, categoryName, detailsTo, bookTo }: ServiceCardProps) {
  return (
    <article className="flex flex-col gap-2 rounded-md border border-border p-4">
      <h2 className="text-lg font-semibold">
        {detailsTo ? (
          <Link className="underline-offset-4 hover:underline" to={detailsTo}>
            {service.name}
          </Link>
        ) : (
          service.name
        )}
      </h2>
      <p className="text-sm text-muted-foreground">
        {categoryName ? `${categoryName} · ` : ''}
        {PERFORMER_LABELS[service.performerType]}
      </p>
      <p className="text-sm">
        {formatPrice(service.price)} · {formatDuration(service.durationMinutes)}
      </p>
      {bookTo ? (
        <Button asChild className="self-start">
          <Link to={bookTo}>Umów</Link>
        </Button>
      ) : null}
    </article>
  );
}
