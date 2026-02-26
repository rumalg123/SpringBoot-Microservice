import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import { queryKeys } from "./keys";

export function useVoteReview(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ reviewId, helpful }: { reviewId: string; helpful: boolean }) => {
      await apiClient!.post(`/reviews/me/${reviewId}/vote`, { helpful });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.reviews.all });
    },
  });
}

export function useReportReview(apiClient: AxiosInstance | null) {
  return useMutation({
    mutationFn: async ({ reviewId, reason }: { reviewId: string; reason: string }) => {
      await apiClient!.post(`/reviews/me/${reviewId}/report`, { reason });
    },
  });
}
