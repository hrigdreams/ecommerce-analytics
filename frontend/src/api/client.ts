import axios from 'axios';

/**
 * Single Axios instance for the Spring Boot backend. Every API call in the
 * app goes through this client so base URL, timeout and error shape stay
 * consistent in one place.
 */
export const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  timeout: 10_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Normalizes an Axios/unknown error into a short, human-readable string for
 * toast/alert feedback (used by OperationsPage's trigger controls, where the
 * user needs to know *why* a POST/PATCH failed, not a raw stack trace).
 */
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const backendMessage =
      (error.response?.data as { message?: string } | undefined)?.message;
    if (backendMessage) return backendMessage;
    if (error.response) {
      return `Request failed: ${error.response.status} ${error.response.statusText}`;
    }
    if (error.request) {
      return 'No response from server -- is the backend running on localhost:8080?';
    }
  }
  if (error instanceof Error) return error.message;
  return 'Unknown error';
}

/** SWR-compatible GET fetcher: `useSWR(key, fetcher)`. */
export const fetcher = <T,>(url: string): Promise<T> =>
  apiClient.get<T>(url).then((res) => res.data);
