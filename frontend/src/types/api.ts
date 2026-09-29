/** Calendar date without time, `YYYY-MM-DD` (e.g. a purchase date). */
export type ISODate = string;

/** ISO-8601 instant, e.g. `2026-09-28T10:15:30Z`. */
export type ISODateTime = string;

/** Error body returned by every failing API call (see docs/api-contract.md). */
export interface ApiErrorBody {
  success: false;
  code: string;
  message: string;
  /** Present on VALIDATION_ERROR: field name -> human readable message. */
  fieldErrors?: Record<string, string>;
}
