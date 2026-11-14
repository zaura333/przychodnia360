import { describe, expect, it } from 'vitest';
import { can, isRole, ROLE_ACTIONS, ROLES } from './index';

describe('permissions', () => {
  it('every role has a defined action list', () => {
    for (const role of ROLES) {
      expect(ROLE_ACTIONS[role].length).toBeGreaterThan(0);
    }
  });

  it('grants area access by role and denies others', () => {
    expect(can({ role: 'PATIENT' }, 'area:patient')).toBe(true);
    expect(can({ role: 'PATIENT' }, 'area:admin')).toBe(false);
    expect(can({ role: 'DOCTOR' }, 'area:staff')).toBe(true);
    expect(can({ role: 'TENANT_ADMIN' }, 'area:platform')).toBe(false);
  });

  it('supports extra permissions and anonymous subjects', () => {
    expect(can({ role: 'RECEPTIONIST', extraActions: ['area:admin'] }, 'area:admin')).toBe(true);
    expect(can(null, 'area:patient')).toBe(false);
    expect(can(undefined, 'area:patient')).toBe(false);
  });

  it('validates role names', () => {
    expect(isRole('DOCTOR')).toBe(true);
    expect(isRole('ROOT')).toBe(false);
    expect(isRole(5)).toBe(false);
  });
});
