export type SystemConfig = {
  id: string;
  configKey: string;
  configValue: string;
  description: string | null;
  valueType: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type FeatureFlag = {
  id: string;
  flagKey: string;
  description: string | null;
  enabled: boolean;
  enabledForRoles: string | null;
  rolloutPercentage: number | null;
  createdAt: string;
  updatedAt: string;
};
