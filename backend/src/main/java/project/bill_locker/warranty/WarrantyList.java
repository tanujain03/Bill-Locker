package project.bill_locker.warranty;

import java.util.List;

/** The warranties page: how many per status (for the tabs) and the rows to show. */
public record WarrantyList(Counts counts, List<WarrantyView> items) {

	public record Counts(long all, long active, long expiringSoon, long expired, long noInfo) {

		static Counts of(List<WarrantyView> views) {
			return new Counts(views.size(), count(views, WarrantyStatus.ACTIVE), count(views, WarrantyStatus.EXPIRING_SOON),
					count(views, WarrantyStatus.EXPIRED), count(views, WarrantyStatus.NO_INFO));
		}

		private static long count(List<WarrantyView> views, WarrantyStatus status) {
			return views.stream().filter(v -> v.status() == status).count();
		}
	}
}
