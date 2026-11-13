import * as CheckboxPrimitive from '@radix-ui/react-checkbox';
import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import * as TooltipPrimitive from '@radix-ui/react-tooltip';
import { Check } from 'lucide-react';
import type { ComponentProps } from 'react';
import { cn } from '@/shared/lib/cn';

export function Checkbox({ className, ...props }: ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        'touch-target flex size-6 items-center justify-center rounded border border-input bg-background data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground',
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator>
        <Check aria-hidden="true" className="size-4" />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

export const Tabs = TabsPrimitive.Root;
export function TabsList({ className, ...props }: ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      className={cn('inline-flex rounded-md bg-muted p-1', className)}
      {...props}
    />
  );
}
export function TabsTrigger({ className, ...props }: ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        'touch-target rounded px-3 text-sm font-medium data-[state=active]:bg-background data-[state=active]:shadow-sm',
        className,
      )}
      {...props}
    />
  );
}
export const TabsContent = TabsPrimitive.Content;

export const TooltipProvider = TooltipPrimitive.Provider;
export const Tooltip = TooltipPrimitive.Root;
export const TooltipTrigger = TooltipPrimitive.Trigger;
export function TooltipContent({
  className,
  ...props
}: ComponentProps<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        sideOffset={4}
        className={cn('rounded-md bg-foreground px-2 py-1 text-xs text-background', className)}
        {...props}
      />
    </TooltipPrimitive.Portal>
  );
}

/** Menu bez trybu modalnego: tryb modalny używa blokady przewijania, która wstrzykuje style (CSP). */
export function DropdownMenu(props: ComponentProps<typeof DropdownMenuPrimitive.Root>) {
  return <DropdownMenuPrimitive.Root modal={false} {...props} />;
}
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;
export function DropdownMenuContent({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Content>) {
  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        sideOffset={4}
        className={cn(
          'min-w-40 rounded-md border border-border bg-background p-1 shadow-md',
          className,
        )}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  );
}
export function DropdownMenuItem({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Item>) {
  return (
    <DropdownMenuPrimitive.Item
      className={cn(
        'touch-target flex cursor-pointer items-center rounded px-2 text-sm data-[highlighted]:bg-muted',
        className,
      )}
      {...props}
    />
  );
}
