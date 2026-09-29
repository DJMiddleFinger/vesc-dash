; VESC Dash - coast regen ("engine braking") for a thumb throttle on ADC1
; with a regen brake lever on ADC2.
;
; Needs: VESC firmware 6.x with LispBM, and
;   App Settings -> ADC -> Control Type = "Current No Reverse Brake ADC2"
;   Motor wheel diameter / poles / gear ratio set in VESC Tool (for the 1 mph check)
;
; What it does
;   Throttle released + brake lever not pulled + faster than 1 mph
;     -> regen at COAST-LEVEL of your brake current. The active VESC Dash drive
;        mode's Regen % scales this, just like it scales the lever.
;   Touch the throttle, pull the lever, or slow below 1 mph
;     -> control goes straight back to the normal ADC app.
;
; Fail-safe: the script only pauses the ADC app 100 ms at a time and renews that
; while coasting. If the script stops for any reason, the ADC app (throttle AND
; lever) takes back over within 0.1 s.
;
; Upload: VESC Tool -> VESC Dev Tools -> LispBM -> paste this -> Upload.
; It then starts automatically every time the VESC powers on.
; Test with the wheel off the ground first.

; ---- settings you can change ---------------------------------------------------

(def min-speed 0.45)      ; m/s. 0.45 m/s = 1 mph
(def coast-level 0.4)     ; 0.0-1.0 of the brake current, before the mode's Regen %
(def ramp-time 0.4)       ; seconds to ease the coast regen in
(def released 0.03)       ; throttle below 3 % counts as released
(def lever-pressed 0.05)  ; lever above 5 % counts as pulled

; ---- script -------------------------------------------------------------------

(def dt 0.02)             ; loop every 20 ms

; Throttle and lever mapping from the ADC app (works for inverted mappings too)
(def v1-start (conf-get 'adc-v1-start))
(def v1-end (conf-get 'adc-v1-end))
(def v2-start (conf-get 'adc-v2-start))
(def v2-end (conf-get 'adc-v2-end))

; Raw voltage -> 0..1 using a start/end mapping
(defun decode (v start end)
  (if (= start end)
      0.0
      (max 0.0 (min 1.0 (/ (- v start) (- end start))))))

(def level 0.0)

(loopwhile t
  (progn
    (let ((thr (decode (get-adc 0) v1-start v1-end))
          (lever (decode (get-adc 1) v2-start v2-end))
          (speed (abs (get-speed))))
      (if (and (< thr released) (< lever lever-pressed) (> speed min-speed))
          (progn
            ; ease in, then hold at coast-level
            (setq level (min coast-level (+ level (* coast-level (/ dt ramp-time)))))
            (app-disable-output 100)
            (set-brake-rel level)
            (timeout-reset))
          (if (> level 0.0)
              (progn
                (setq level 0.0)
                (app-disable-output 0)))))
    (sleep dt)))
