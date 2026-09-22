(module
  ;; Two imported tags with the same signature are still distinct tags: a handler
  ;; for one must not catch an exception thrown with the other.
  (import "host" "a" (tag $a (param i32)))
  (import "host" "b" (tag $b (param i32)))

  ;; 1 if the exception thrown with $a is caught by the handler for $b
  (func (export "b-catches-a") (result i32)
    (block $h (result i32)
      (try_table (result i32) (catch $b $h)
        (throw $a (i32.const 7)))))

  ;; the same tag catches itself and yields its payload
  (func (export "a-catches-a") (result i32)
    (block $h (result i32)
      (try_table (result i32) (catch $a $h)
        (throw $a (i32.const 7)))))
)
