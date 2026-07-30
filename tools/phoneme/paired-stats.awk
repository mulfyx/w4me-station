BEGIN {
	FS = ","
	failed = 0
	count = 0
	max_resolution = 0
	wins = 0
	losses = 0
	ties = 0
	baseline_first = 0
	candidate_first = 0
}

NR == 1 && $1 == "sample" {
	next
}

NF != 5 {
	printf "error: malformed paired sample row %d\n", NR > "/dev/stderr"
	failed = 1
	next
}

{
	sample = $1 + 0
	baseline = $2 + 0
	candidate = $3 + 0
	frames = $4 + 0
	order = $5
	if (baseline <= 0 || candidate <= 0 || frames <= 0 \
		|| (order != "baseline-first" && order != "candidate-first")) {
		printf "error: invalid paired sample row %d\n", NR > "/dev/stderr"
		failed = 1
		next
	}
	if (sample in seen) {
		printf "error: duplicate paired sample %d\n", sample > "/dev/stderr"
		failed = 1
		next
	}
	seen[sample] = 1
	count++
	deltas[count] = baseline - candidate
	percentages[count] = (baseline - candidate) * 100.0 / baseline
	resolution = 1000.0 / frames
	if (resolution > max_resolution) {
		max_resolution = resolution
	}
	if (baseline > candidate) {
		wins++
	} else if (baseline < candidate) {
		losses++
	} else {
		ties++
	}
	if (order == "baseline-first") {
		baseline_first++
	} else {
		candidate_first++
	}
}

function absolute(value) {
	return value < 0 ? -value : value
}

function median(values, value_count,    i, j, temporary) {
	for (i = 2; i <= value_count; i++) {
		temporary = values[i]
		j = i - 1
		while (j >= 1 && values[j] > temporary) {
			values[j + 1] = values[j]
			j--
		}
		values[j + 1] = temporary
	}
	if (value_count % 2 == 1) {
		return values[(value_count + 1) / 2]
	}
	return (values[value_count / 2] + values[value_count / 2 + 1]) / 2.0
}

END {
	if (failed) {
		exit 2
	}
	if (count == 0) {
		print "error: no paired samples" > "/dev/stderr"
		exit 2
	}
	median_delta = median(deltas, count)
	median_percentage = median(percentages, count)
	timer_resolved = absolute(median_delta) + 0.0000001 >= max_resolution
	order_balanced = baseline_first == candidate_first
	source_clean = source_dirty == "no"
	evidence_quality = count >= 8 && order_balanced && timer_resolved \
		&& source_clean \
		? "measured" : "exploratory"
	printf "pairs=%d median-delta-us-per-frame=%.1f median-speedup-percent=%.3f", \
		count, median_delta, median_percentage
	printf " wins=%d losses=%d ties=%d", wins, losses, ties
	printf " timer-resolution-us-per-frame=%.3f timer-resolved=%s", \
		max_resolution, timer_resolved ? "yes" : "no"
	printf " order-balanced=%s source-clean=%s", \
		order_balanced ? "yes" : "no", source_clean ? "yes" : "no"
	printf " evidence-quality=%s\n", evidence_quality
}
